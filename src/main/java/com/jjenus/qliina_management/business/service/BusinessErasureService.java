package com.jjenus.qliina_management.business.service;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Consent-confirmed erasure of every row belonging to a tenant business (N-2 purge).
 *
 * <p>Deletes run in strict topological order so no foreign-key constraint is ever
 * violated, then the business row itself is removed. Global tables are deliberately
 * <b>never</b> touched: {@code Role}, {@code Permission}, {@code BillingPlan},
 * {@code PlanVersion}, {@code PlanFeature}, {@code Coupon}, {@code SystemSetting},
 * {@code PlatformPaymentProviderConfig} (cleanup of orphaned platform rows is not this
 * service's job).</p>
 *
 * <p>Ordering contract (re-verify after any schema change):
 * <ol>
 *   <li>element-collection join tables (native SQL — Hibernate drops these via cascade
 *       only on managed entities, and bare bulk deletes skip them);</li>
 *   <li>user-scoped identity rows (tokens, auth accounts, devices, preferences, roles);</li>
 *   <li>children whose rows have no {@code business_id} column — reached via a subquery
 *       through their {@code businessId}-scoped parent;</li>
 *   <li>the subscription-scoped billing tree;</li>
 *   <li>every {@code BaseTenantEntity} bulk delete, children before parents;</li>
 *   <li>business-owned leftovers ({@code BusinessConfig}, {@code Business} last).</li>
 * </ol></p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BusinessErasureService {

    private final EntityManager em;

    /**
     * Tenant bulk deletes, children strictly before parents. Every entry either extends
     * {@code BaseTenantEntity} (inherited {@code businessId}) or declares its own
     * {@code businessId} property, and has no remaining FK children at its position.
     */
    private static final String[] TENANT_BULK_ORDER = {
        "Complaint",
        "SecurityEvent",
        "ComplianceReport",
        "ConsentRecord",
        "DataSubjectRequest",
        "DataRetentionPolicy",
        "AuditLog",
        "EmailConfiguration",
        "SMSConfiguration",
        "PushNotificationConfiguration",
        "NotificationTemplate",
        "NotificationOutbox",
        "NotificationDelivery",
        "NotificationDeliveryEvent",
        "NotificationLog",      // FK -> Notification (its own business_id)
        "Notification",         // after NotificationLog / NotificationDelivery*
        "CashDrawerSession",
        "GarmentType",
        "ServiceType",
        "ServiceGarmentPricing",
        "LoyaltyTier",
        "LoyaltyReward",
        "Expense",
        "Attendance",
        "TimeEntry",
        "EmployeeShift",
        "EmployeeSchedule",
        "EmployeePerformance",
        "EmployeeTarget",
        "PaymentMethod",
        "PaymentProviderConfig",
        "ShopStock",            // FK -> InventoryItem
        "StockAlert",           // FK -> InventoryItem
        "StockTransaction",     // FK -> InventoryItem
        "StockRequest",         // FK -> InventoryItem
        "InventoryItem",        // after shop-stock rows + PurchaseOrderItem (step 3)
        "Shop",                 // after user_shops + shop_operating_hours (step 1)
        "PurchaseOrder",        // after PurchaseOrderItem (step 3)
        "Supplier",             // after supplier_categories (step 1) + PurchaseOrder
        "OrderDiscrepancy",     // FK -> Order (has its own business_id)
        "OrderPayment",         // FK -> Order — MUST precede Order
        "Order",                // after order_tags/notes/timeline/items/discrepancies/payments
        "Invoice",              // after InvoiceItem (step 3)
        "QualityChecklist",     // after ChecklistItem (step 3)
        "QualityCheck",         // after CheckResult + Defect (step 3)
        "Customer",             // after address/note/preferences/loyalty children (step 3)
        "CorporateAccount",
        "User",                 // last — all user-scoped rows are gone by now
    };

    @Transactional
    public int erase(UUID businessId) {
        int total = 0;

        List<UUID> userIds = ids("select u.id from User u where u.businessId = :bid", businessId);
        List<UUID> subIds = ids("select s.id from Subscription s where s.businessId = :bid", businessId);
        List<UUID> orderItemIds =
                ids("select i.id from OrderItem i where i.order.businessId = :bid", businessId);
        List<UUID> defectIds =
                ids("select d.id from Defect d where d.qualityCheck.businessId = :bid", businessId);

        // ── 1. Element-collection join tables (native SQL) ──────────────────────
        total += nativeDelete("DELETE FROM order_tags WHERE order_id IN (SELECT id FROM orders WHERE business_id = :bid)", businessId);
        total += nativeDelete("DELETE FROM supplier_categories WHERE supplier_id IN (SELECT id FROM suppliers WHERE business_id = :bid)", businessId);
        total += nativeDelete("DELETE FROM shop_operating_hours WHERE shop_id IN (SELECT id FROM shops WHERE business_id = :bid)", businessId);
        total += nativeDelete("DELETE FROM user_shops WHERE user_id IN (SELECT id FROM users WHERE business_id = :bid)", businessId);
        // Element-collection children of association-scoped parents: rewritten as plain
        // SQL subqueries (Hibernate 7 lifts a broken collection-cleanup subquery when a
        // bulk delete filters on an association path, e.g. `delete OrderItem where order.businessId`).
        total += nativeDelete("DELETE FROM item_images WHERE item_id IN "
                + "(SELECT id FROM order_items WHERE order_id IN (SELECT id FROM orders WHERE business_id = :bid))", businessId);
        total += nativeDelete("DELETE FROM defect_images WHERE defect_id IN "
                + "(SELECT id FROM defects WHERE quality_check_id IN (SELECT id FROM quality_checks WHERE business_id = :bid))", businessId);

        // ── 2. User-scoped identity rows ────────────────────────────────────────
        total += jpqlList("delete from RefreshToken t where t.user.id in :ids", userIds);
        total += jpqlList("delete from PasswordResetToken t where t.user.id in :ids", userIds);
        total += jpqlList("delete from AuthAccount a where a.user.id in :ids", userIds);
        total += jpqlList("delete from UserDevice d where d.userId in :ids", userIds);
        total += jpql("delete from UserRole r where r.businessId = :bid", businessId);
        total += jpql("delete from UserPermission p where p.businessId = :bid", businessId);
        total += jpql("delete from UserNotificationPreference n where n.businessId = :bid", businessId);

        // ── 3. Children without a business_id column (subquery via parent) ──────
        total += jpqlList("delete from ItemStatusHistory h where h.orderItem.id in :ids", orderItemIds);
        total += jpqlList("delete from OrderItemUnit u where u.orderItem.id in :ids", orderItemIds);
        // id-based (not path-expression) WHERE: bulk-deleting an entity with element
        // collections through an association path triggers a broken cleanup subquery.
        total += jpqlList("delete from OrderItem i where i.id in :ids", orderItemIds);
        total += jpql("delete from OrderNote n where n.order.businessId = :bid", businessId);
        total += jpql("delete from OrderTimeline t where t.order.businessId = :bid", businessId);
        total += jpql("delete from InvoiceItem i where i.invoice.businessId = :bid", businessId);
        total += jpql("delete from ChecklistItem c where c.checklist.businessId = :bid", businessId);
        total += jpql("delete from CheckResult r where r.qualityCheck.businessId = :bid", businessId);
        total += jpqlList("delete from Defect d where d.id in :ids", defectIds);
        total += jpql("delete from CustomerAddress a where a.customer.businessId = :bid", businessId);
        total += jpql("delete from CustomerNote n where n.customer.businessId = :bid", businessId);
        total += jpql("delete from CustomerPreferences p where p.customer.businessId = :bid", businessId);
        total += jpql("delete from LoyaltyTransaction t where t.customer.businessId = :bid", businessId);
        total += jpql("delete from PurchaseOrderItem i where i.purchaseOrder.businessId = :bid", businessId);

        // ── 4. Subscription-scoped billing tree ─────────────────────────────────
        if (!subIds.isEmpty()) {
            total += jpqlList("delete from SubscriptionFeature f where f.subscription.id in :ids", subIds);
            total += jpqlList("delete from SubscriptionCoupon c where c.subscription.id in :ids", subIds);
            total += jpqlList("delete from InvoiceLineItem l where l.invoice.id in "
                    + "(select bi.id from BillingInvoice bi where bi.subscriptionId in :ids)", subIds);
            total += jpqlList("delete from BillingPayment p where p.invoiceId in "
                    + "(select bi.id from BillingInvoice bi where bi.subscriptionId in :ids)", subIds);
            total += jpqlList("delete from BillingInvoice bi where bi.subscriptionId in :ids", subIds);
            total += jpqlList("delete from SubscriptionEvent e where e.subscriptionId in :ids", subIds);
            total += jpqlList("delete from UsageRecord u where u.subscriptionId in :ids", subIds);
            total += jpql("delete from Subscription s where s.businessId = :bid", businessId);
        }
        total += jpql("delete from CouponRedemption c where c.businessId = :bid", businessId);
        total += jpql("delete from PaymentReconciliationItem p where p.businessId = :bid", businessId);
        total += jpql("delete from BillingPaymentMethod m where m.businessId = :bid", businessId);
        total += jpql("delete from Refund r where r.businessId = :bid", businessId);

        // ── 5. Tenant bulk (children before parents) ────────────────────────────
        for (String entity : TENANT_BULK_ORDER) {
            total += jpql("delete from " + entity + " t where t.businessId = :bid", businessId);
        }

        // ── 6. Business-owned leftovers, business last ──────────────────────────
        total += jpql("delete from BusinessConfig c where c.businessId = :bid", businessId);
        total += jpql("delete from Business b where b.id = :bid", businessId);

        log.info("Erasure complete for businessId={}: {} rows deleted across all FK-ordered passes", businessId, total);
        return total;
    }

    // ------------------------------------------------------------------ helpers

    private List<UUID> ids(String hql, UUID businessId) {
        return em.createQuery(hql, UUID.class).setParameter("bid", businessId).getResultList();
    }

    private int jpql(String hql, UUID businessId) {
        return em.createQuery(hql).setParameter("bid", businessId).executeUpdate();
    }

    private int jpqlList(String hql, List<UUID> ids) {
        if (ids.isEmpty()) return 0; // HQL refuses `IN ()`
        return em.createQuery(hql).setParameter("ids", ids).executeUpdate();
    }

    private int nativeDelete(String sql, UUID businessId) {
        return em.createNativeQuery(sql).setParameter("bid", businessId).executeUpdate();
    }
}