package com.jjenus.qliina_management.integration;

import com.jjenus.qliina_management.audit.model.AuditLog;
import com.jjenus.qliina_management.audit.model.ComplianceReport;
import com.jjenus.qliina_management.audit.model.ConsentRecord;
import com.jjenus.qliina_management.audit.model.DataRetentionPolicy;
import com.jjenus.qliina_management.audit.model.DataSubjectRequest;
import com.jjenus.qliina_management.audit.model.SecurityEvent;
import com.jjenus.qliina_management.billing.model.BillingInvoice;
import com.jjenus.qliina_management.billing.model.BillingPayment;
import com.jjenus.qliina_management.billing.model.BillingPaymentMethod;
import com.jjenus.qliina_management.billing.model.BillingPlan;
import com.jjenus.qliina_management.billing.model.Coupon;
import com.jjenus.qliina_management.billing.model.CouponRedemption;
import com.jjenus.qliina_management.billing.model.CouponStatus;
import com.jjenus.qliina_management.billing.model.DiscountType;
import com.jjenus.qliina_management.billing.model.InvoiceLineItem;
import com.jjenus.qliina_management.billing.model.InvoiceStatus;
import com.jjenus.qliina_management.billing.model.LineItemType;
import com.jjenus.qliina_management.billing.model.PaymentMethodType;
import com.jjenus.qliina_management.billing.model.PaymentStatus;
import com.jjenus.qliina_management.billing.model.PlanStatus;
import com.jjenus.qliina_management.billing.model.PlanVersion;
import com.jjenus.qliina_management.billing.model.Subscription;
import com.jjenus.qliina_management.billing.model.SubscriptionCoupon;
import com.jjenus.qliina_management.billing.model.SubscriptionCouponId;
import com.jjenus.qliina_management.billing.model.SubscriptionEvent;
import com.jjenus.qliina_management.billing.model.SubscriptionFeature;
import com.jjenus.qliina_management.billing.model.SubscriptionStatus;
import com.jjenus.qliina_management.billing.model.UsageRecord;
import com.jjenus.qliina_management.billing.repository.BillingPlanRepository;
import com.jjenus.qliina_management.billing.repository.CouponRepository;
import com.jjenus.qliina_management.billing.repository.PlanVersionRepository;
import com.jjenus.qliina_management.business.model.GarmentType;
import com.jjenus.qliina_management.business.model.ServiceGarmentPricing;
import com.jjenus.qliina_management.business.model.ServiceType;
import com.jjenus.qliina_management.business.model.Shop;
import com.jjenus.qliina_management.business.repository.ShopRepository;
import com.jjenus.qliina_management.complaint.model.Complaint;
import com.jjenus.qliina_management.complaint.model.ComplaintCategory;
import com.jjenus.qliina_management.complaint.model.ComplaintSeverity;
import com.jjenus.qliina_management.complaint.model.ComplaintStatus;
import com.jjenus.qliina_management.customer.model.Customer;
import com.jjenus.qliina_management.customer.model.CustomerAddress;
import com.jjenus.qliina_management.customer.model.CustomerNote;
import com.jjenus.qliina_management.customer.model.CustomerPreferences;
import com.jjenus.qliina_management.customer.model.LoyaltyTransaction;
import com.jjenus.qliina_management.customer.model.LoyaltyTier;
import com.jjenus.qliina_management.employee.model.Attendance;
import com.jjenus.qliina_management.employee.model.EmployeePerformance;
import com.jjenus.qliina_management.employee.model.EmployeeSchedule;
import com.jjenus.qliina_management.employee.model.EmployeeShift;
import com.jjenus.qliina_management.employee.model.EmployeeTarget;
import com.jjenus.qliina_management.employee.model.TimeEntry;
import com.jjenus.qliina_management.expense.model.Expense;
import com.jjenus.qliina_management.expense.model.ExpenseCategory;
import com.jjenus.qliina_management.identity.model.AuthAccount;
import com.jjenus.qliina_management.identity.model.BusinessConfig;
import com.jjenus.qliina_management.identity.model.OperatingHour;
import com.jjenus.qliina_management.identity.model.PasswordResetToken;
import com.jjenus.qliina_management.identity.model.Permission;
import com.jjenus.qliina_management.identity.model.RefreshToken;
import com.jjenus.qliina_management.identity.model.Role;
import com.jjenus.qliina_management.identity.model.User;
import com.jjenus.qliina_management.identity.model.UserPermission;
import com.jjenus.qliina_management.identity.model.UserRole;
import com.jjenus.qliina_management.identity.repository.PermissionRepository;
import com.jjenus.qliina_management.identity.repository.RoleRepository;
import com.jjenus.qliina_management.inventory.model.InventoryItem;
import com.jjenus.qliina_management.inventory.model.PurchaseOrder;
import com.jjenus.qliina_management.inventory.model.PurchaseOrderItem;
import com.jjenus.qliina_management.inventory.model.ShopStock;
import com.jjenus.qliina_management.inventory.model.StockAlert;
import com.jjenus.qliina_management.inventory.model.StockRequest;
import com.jjenus.qliina_management.inventory.model.StockTransaction;
import com.jjenus.qliina_management.inventory.model.Supplier;
import com.jjenus.qliina_management.notification.model.EmailConfiguration;
import com.jjenus.qliina_management.notification.model.Notification;
import com.jjenus.qliina_management.notification.model.NotificationDelivery;
import com.jjenus.qliina_management.notification.model.NotificationDeliveryEvent;
import com.jjenus.qliina_management.notification.model.NotificationDeliveryEventType;
import com.jjenus.qliina_management.notification.model.NotificationDeliveryStatus;
import com.jjenus.qliina_management.notification.model.NotificationLog;
import com.jjenus.qliina_management.notification.model.NotificationOutbox;
import com.jjenus.qliina_management.notification.model.NotificationOutboxStatus;
import com.jjenus.qliina_management.notification.model.NotificationTemplate;
import com.jjenus.qliina_management.notification.model.PushNotificationConfiguration;
import com.jjenus.qliina_management.notification.model.SMSConfiguration;
import com.jjenus.qliina_management.notification.model.UserDevice;
import com.jjenus.qliina_management.notification.model.UserNotificationPreference;
import com.jjenus.qliina_management.order.model.ItemStatusHistory;
import com.jjenus.qliina_management.order.model.Order;
import com.jjenus.qliina_management.order.model.OrderDiscrepancy;
import com.jjenus.qliina_management.order.model.OrderItem;
import com.jjenus.qliina_management.order.model.OrderItemUnit;
import com.jjenus.qliina_management.order.model.OrderNote;
import com.jjenus.qliina_management.order.model.OrderTimeline;
import com.jjenus.qliina_management.payment.model.CashDrawerSession;
import com.jjenus.qliina_management.payment.model.CorporateAccount;
import com.jjenus.qliina_management.payment.model.Invoice;
import com.jjenus.qliina_management.payment.model.InvoiceItem;
import com.jjenus.qliina_management.payment.model.OrderPayment;
import com.jjenus.qliina_management.payment.model.PaymentMethod;
import com.jjenus.qliina_management.payment.model.PaymentProviderConfig;
import com.jjenus.qliina_management.payment.model.PaymentReconciliationItem;
import com.jjenus.qliina_management.payment.model.ProviderConnectionMode;
import com.jjenus.qliina_management.payment.model.Refund;
import com.jjenus.qliina_management.quality.model.CheckResult;
import com.jjenus.qliina_management.quality.model.ChecklistItem;
import com.jjenus.qliina_management.quality.model.Defect;
import com.jjenus.qliina_management.quality.model.QualityCheck;
import com.jjenus.qliina_management.quality.model.QualityChecklist;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * N-3 (platform admin provisions a tenant business + owner with a temporary
 * password) and N-2 (archive / restore / cancel / consent-confirmed purge)
 * coverage for the admin business lifecycle API.
 */
class AdminBusinessLifecycleIntegrationTest extends BaseIntegrationTest {

    @Autowired private EntityManager em;
    @Autowired private PlatformTransactionManager txManager;
    @Autowired private ShopRepository shopRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PermissionRepository permissionRepository;
    @Autowired private CouponRepository couponRepository;
    @Autowired private BillingPlanRepository billingPlanRepository;
    @Autowired private PlanVersionRepository planVersionRepository;

    private <T> T tx(TransactionCallback<T> callback) {
        return new TransactionTemplate(txManager).execute(callback);
    }

    // ---------------------------------------------------------------------
    // Fixtures
    // ---------------------------------------------------------------------

    private record CreatedWithOwner(UUID businessId, UUID ownerId, String ownerUsername, String ownerEmail, String tempPassword) {}

    private Map<String, Object> createBody(String name) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        return body;
    }

    private Map<String, Object> createBodyWithOwner(String name, String username, String email) {
        Map<String, Object> body = createBody(name);
        body.put("ownerUsername", username);
        body.put("ownerEmail", email);
        body.put("ownerFirstName", "Owner");
        body.put("ownerLastName", "One");
        return body;
    }

    private UUID createBusinessPlain(String name) throws Exception {
        String json = post("/api/v1/admin/businesses", adminToken(), createBody(name))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return readUuid(json, "$.business.id");
    }

    private CreatedWithOwner createBusinessWithOwner() throws Exception {
        String username = "owner_" + random();
        String email = username + "@test.com";
        String json = post("/api/v1/admin/businesses", adminToken(),
                createBodyWithOwner("Lifecycle " + random(), username, email))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return new CreatedWithOwner(
                readUuid(json, "$.business.id"),
                readUuid(json, "$.owner.userId"),
                readString(json, "$.owner.username"),
                readString(json, "$.owner.email"),
                readString(json, "$.owner.temporaryPassword"));
    }

    private void archive(UUID businessId) throws Exception {
        patch("/api/v1/admin/businesses/" + businessId + "/status", adminToken(),
                Map.of("status", "ARCHIVED", "reason", "Owner requested closure"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ARCHIVED"));
    }

    private void cancel(UUID businessId) throws Exception {
        patch("/api/v1/admin/businesses/" + businessId + "/status", adminToken(),
                Map.of("status", "CANCELLED", "reason", "Business closed"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    // ---------------------------------------------------------------------
    // N-3 — business creation + owner provisioning
    // ---------------------------------------------------------------------

    @Test
    void createBusiness_withOwner_provisionsOwnerShopAndAdminRole() throws Exception {
        String name = "Lifecycle " + random();
        String username = "created_owner_" + random();
        String email = username + "@test.com";

        ResultActions rs = post("/api/v1/admin/businesses", adminToken(),
                createBodyWithOwner(name, username, email));
        String json = rs.andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        UUID businessId = readUuid(json, "$.business.id");
        assertNotNull(businessId);
        assertEquals(name, readString(json, "$.business.name"));
        assertEquals("TRIAL", readString(json, "$.business.status"));
        assertEquals("FREE", readString(json, "$.business.plan"));
        assertNotNull(readString(json, "$.business.slug"));
        assertNotNull(readString(json, "$.business.createdAt"));

        assertEquals(username, readString(json, "$.owner.username"));
        assertEquals(email, readString(json, "$.owner.email"));
        UUID ownerId = readUuid(json, "$.owner.userId");
        assertNotNull(ownerId);
        String tempPassword = readString(json, "$.owner.temporaryPassword");
        assertNotNull(tempPassword);
        assertFalse(tempPassword.isBlank());
        assertTrue(tempPassword.length() >= 12);

        // owner can log in immediately with the one-time credentials
        assertNotNull(loginToken(username, tempPassword));

        // exactly one active shop for the new business
        List<Shop> shops = shopRepository.findActiveByBusinessId(businessId);
        assertEquals(1, shops.size());

        // owner is wired to BUSINESS_ADMIN for this business (JPQL, not detached roles)
        Long roleCount = tx(t -> em.createQuery(
                        "select count(ur) from UserRole ur where ur.user.id = :uid and ur.businessId = :b and ur.role.name = :rn",
                        Long.class)
                .setParameter("uid", ownerId)
                .setParameter("b", businessId)
                .setParameter("rn", "BUSINESS_ADMIN")
                .getSingleResult());
        assertEquals(1L, roleCount);
    }

    @Test
    void createBusiness_withoutOwner_ownerIsNull() throws Exception {
        String json = post("/api/v1/admin/businesses", adminToken(), createBody("NoOwner " + random()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertNotNull(readUuid(json, "$.business.id"));
        assertNull(readString(json, "$.owner"));
    }

    @Test
    void createBusiness_partialOwner_validationError() throws Exception {
        Map<String, Object> body = createBody("PartialOwner " + random());
        body.put("ownerUsername", "owner_" + random()); // missing email / first / last name
        ResultActions rs = post("/api/v1/admin/businesses", adminToken(), body);
        assertProblemDetail(rs, 400, "VALIDATION_ERROR");
        rs.andExpect(jsonPath("$.field").value("owner"));
    }

    @Test
    void createBusiness_invalidOwnerEmail_validationError() throws Exception {
        Map<String, Object> body = createBody("BadEmail " + random());
        body.put("ownerUsername", "owner_" + random());
        body.put("ownerEmail", "not-an-email");
        body.put("ownerFirstName", "Owner");
        body.put("ownerLastName", "One");
        ResultActions rs = post("/api/v1/admin/businesses", adminToken(), body);
        assertProblemDetail(rs, 400, "VALIDATION_ERROR");
        rs.andExpect(jsonPath("$.field").value("ownerEmail"));
    }

    @Test
    void createBusiness_duplicateOwnerUsername_usernameExists() throws Exception {
        String username = "shared_" + random();
        post("/api/v1/admin/businesses", adminToken(), createBodyWithOwner("First " + random(), username, username + "@test.com"))
                .andExpect(status().isCreated());
        ResultActions rs = post("/api/v1/admin/businesses", adminToken(),
                createBodyWithOwner("Second " + random(), username, username + "@other.com"));
        assertProblemDetail(rs, 400, "USERNAME_EXISTS");
        rs.andExpect(jsonPath("$.field").value("ownerUsername"));
    }

    @Test
    void createBusiness_duplicateOwnerEmail_emailExists() throws Exception {
        String username = "email_owner_" + random();
        String email = username + "@test.com";
        post("/api/v1/admin/businesses", adminToken(), createBodyWithOwner("First " + random(), username, email))
                .andExpect(status().isCreated());
        ResultActions rs = post("/api/v1/admin/businesses", adminToken(),
                createBodyWithOwner("Second " + random(), "other_" + random(), email));
        assertProblemDetail(rs, 400, "EMAIL_EXISTS");
        rs.andExpect(jsonPath("$.field").value("ownerEmail"));
    }

    @Test
    void createBusiness_invalidPlan_badRequest() throws Exception {
        Map<String, Object> body = createBody("BadPlan " + random());
        body.put("plan", "ULTRA");
        ResultActions rs = post("/api/v1/admin/businesses", adminToken(), body);
        assertProblemDetail(rs, 400, "INVALID_PLAN");
        rs.andExpect(jsonPath("$.field").value("plan"));
    }

    // ---------------------------------------------------------------------
    // N-2 — archive / restore / cancel
    // ---------------------------------------------------------------------

    @Test
    void archiveBusiness_withoutReason_validationError() throws Exception {
        UUID businessId = createBusinessPlain("ArchiveNoReason " + random());
        ResultActions rs = patch("/api/v1/admin/businesses/" + businessId + "/status", adminToken(),
                Map.of("status", "ARCHIVED"));
        assertProblemDetail(rs, 400, "VALIDATION_ERROR");
        rs.andExpect(jsonPath("$.field").value("reason"));
    }

    @Test
    void archiveThenRestore_roundTrip() throws Exception {
        UUID businessId = createBusinessPlain("RoundTrip " + random());
        patch("/api/v1/admin/businesses/" + businessId + "/status", adminToken(),
                Map.of("status", "ARCHIVED", "reason", "Temporary closure"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ARCHIVED"));
        patch("/api/v1/admin/businesses/" + businessId + "/status", adminToken(),
                Map.of("status", "ACTIVE"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void cancelledBusiness_cannotBeRestored() throws Exception {
        UUID businessId = createBusinessPlain("Cancelled " + random());
        cancel(businessId);
        ResultActions rs = patch("/api/v1/admin/businesses/" + businessId + "/status", adminToken(),
                Map.of("status", "ACTIVE"));
        assertProblemDetail(rs, 400, "INVALID_STATUS_TRANSITION");
        rs.andExpect(jsonPath("$.field").value("status"));
    }

    @Test
    void archivedBusiness_cannotBeCancelled() throws Exception {
        UUID businessId = createBusinessPlain("Archived " + random());
        archive(businessId);
        ResultActions rs = patch("/api/v1/admin/businesses/" + businessId + "/status", adminToken(),
                Map.of("status", "CANCELLED", "reason", "Change of mind"));
        assertProblemDetail(rs, 400, "INVALID_STATUS_TRANSITION");
        rs.andExpect(jsonPath("$.field").value("status"));
    }

    // ---------------------------------------------------------------------
    // N-2 — consent-confirmed purge
    // ---------------------------------------------------------------------

    @Test
    void purgeBusiness_thatIsNotArchivedOrCancelled_invalidState() throws Exception {
        UUID businessId = createBusinessPlain("StillActive " + random());
        ResultActions rs = delete("/api/v1/admin/businesses/" + businessId + "/purge", adminToken(),
                Map.of("confirm", "PURGE"));
        assertProblemDetail(rs, 400, "INVALID_STATE");
        rs.andExpect(jsonPath("$.field").value("status"));
    }

    @Test
    void purgeBusiness_wrongConfirmation_confirmationRequired() throws Exception {
        UUID businessId = createBusinessPlain("WrongConfirm " + random());
        archive(businessId);
        ResultActions rs = delete("/api/v1/admin/businesses/" + businessId + "/purge", adminToken(),
                Map.of("confirm", "delete it"));
        assertProblemDetail(rs, 400, "CONFIRMATION_REQUIRED");
        rs.andExpect(jsonPath("$.field").value("confirm"));
    }

    @Test
    void impersonateArchivedBusiness_businessArchived() throws Exception {
        UUID businessId = createBusinessPlain("ImpArchived " + random());
        archive(businessId);
        ResultActions rs = post("/api/v1/admin/impersonate/" + businessId, adminToken(), Map.of());
        assertProblemDetail(rs, 400, "BUSINESS_ARCHIVED");
    }

    @Test
    void impersonateCancelledBusiness_businessCancelled() throws Exception {
        UUID businessId = createBusinessPlain("ImpCancelled " + random());
        cancel(businessId);
        ResultActions rs = post("/api/v1/admin/impersonate/" + businessId, adminToken(), Map.of());
        assertProblemDetail(rs, 400, "BUSINESS_CANCELLED");
    }

    @Test
    void purgeCancelledBusiness_ok() throws Exception {
        UUID businessId = createBusinessPlain("PurgeCancelled " + random());
        cancel(businessId);
        String json = delete("/api/v1/admin/businesses/" + businessId + "/purge", adminToken(),
                Map.of("confirm", "PURGE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PURGED"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertEquals(businessId.toString(), readString(json, "$.businessId"));
        // a plain (owner-less) business erases just its own row
        assertTrue(readInt(json, "$.rowsDeleted") >= 1);
    }

    @Test
    void purgeArchivedBusiness_erasesFullGraph() throws Exception {
        CreatedWithOwner created = createBusinessWithOwner();
        UUID businessId = created.businessId();
        String couponCode = "PURGE-" + random().toUpperCase();

        // plant a representative row in every erasure bucket
        tx(t -> { seedFullGraph(businessId, couponCode); return null; });

        // archive first (archived -> purge is the consent-confirmed path)
        archive(businessId);

        String json = delete("/api/v1/admin/businesses/" + businessId + "/purge", adminToken(),
                Map.of("confirm", "PURGE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PURGED"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        int rowsDeleted = readInt(json, "$.rowsDeleted");
        assertTrue(rowsDeleted >= 80, "expected full graph erasure, got rowsDeleted=" + rowsDeleted);

        // everything tenant-owned is gone
        tx(t -> {
            assertZero("select count(u) from User u where u.businessId = :bid", businessId);
            assertZero("select count(s) from Shop s where s.businessId = :bid", businessId);
            assertZero("select count(a) from AuthAccount a where a.user.businessId = :bid", businessId);
            assertZero("select count(ur) from UserRole ur where ur.businessId = :bid", businessId);
            assertZero("select count(up) from UserPermission up where up.user.businessId = :bid", businessId);
            assertZero("select count(c) from BusinessConfig c where c.businessId = :bid", businessId);
            assertZero("select count(cu) from Customer cu where cu.businessId = :bid", businessId);
            assertZero("select count(a) from CustomerAddress a where a.customer.businessId = :bid", businessId);
            assertZero("select count(o) from Order o where o.businessId = :bid", businessId);
            assertZero("select count(i) from OrderItem i where i.order.businessId = :bid", businessId);
            assertZero("select count(n) from OrderNote n where n.order.businessId = :bid", businessId);
            assertZero("select count(p) from OrderPayment p where p.businessId = :bid", businessId);
            assertZero("select count(r) from Refund r where r.businessId = :bid", businessId);
            assertZero("select count(v) from Invoice v where v.businessId = :bid", businessId);
            assertZero("select count(q) from QualityChecklist q where q.businessId = :bid", businessId);
            assertZero("select count(q) from QualityCheck q where q.businessId = :bid", businessId);
            assertZero("select count(d) from Defect d where d.qualityCheck.businessId = :bid", businessId);
            assertZero("select count(s) from Supplier s where s.businessId = :bid", businessId);
            assertZero("select count(i) from InventoryItem i where i.businessId = :bid", businessId);
            assertZero("select count(s) from ShopStock s where s.businessId = :bid", businessId);
            assertZero("select count(s) from StockAlert s where s.businessId = :bid", businessId);
            assertZero("select count(s) from StockTransaction s where s.businessId = :bid", businessId);
            assertZero("select count(s) from StockRequest s where s.businessId = :bid", businessId);
            assertZero("select count(p) from PurchaseOrder p where p.businessId = :bid", businessId);
            assertZero("select count(g) from GarmentType g where g.businessId = :bid", businessId);
            assertZero("select count(s) from ServiceGarmentPricing s where s.businessId = :bid", businessId);
            assertZero("select count(s) from Subscription s where s.businessId = :bid", businessId);
            assertZero("select count(f) from SubscriptionFeature f where f.subscription.businessId = :bid", businessId);
            assertZero("select count(b) from BillingInvoice b where b.subscriptionId in (select s.id from Subscription s where s.businessId = :bid)", businessId);
            assertZero("select count(c) from CouponRedemption c where c.businessId = :bid", businessId);
            assertZero("select count(m) from BillingPaymentMethod m where m.businessId = :bid", businessId);
            assertZero("select count(p) from PaymentReconciliationItem p where p.businessId = :bid", businessId);
            assertZero("select count(n) from Notification n where n.businessId = :bid", businessId);
            assertZero("select count(l) from NotificationLog l where l.businessId = :bid", businessId);
            assertZero("select count(d) from NotificationDelivery d where d.businessId = :bid", businessId);
            assertZero("select count(t) from NotificationTemplate t where t.businessId = :bid", businessId);
            assertZero("select count(o) from NotificationOutbox o where o.businessId = :bid", businessId);
            assertZero("select count(e) from EmailConfiguration e where e.businessId = :bid", businessId);
            assertZero("select count(s) from SMSConfiguration s where s.businessId = :bid", businessId);
            assertZero("select count(c) from CashDrawerSession c where c.businessId = :bid", businessId);
            assertZero("select count(m) from PaymentMethod m where m.businessId = :bid", businessId);
            assertZero("select count(p) from PaymentProviderConfig p where p.businessId = :bid", businessId);
            assertZero("select count(e) from Expense e where e.businessId = :bid", businessId);
            assertZero("select count(a) from Attendance a where a.businessId = :bid", businessId);
            assertZero("select count(t) from TimeEntry t where t.businessId = :bid", businessId);
            assertZero("select count(s) from EmployeeShift s where s.businessId = :bid", businessId);
            assertZero("select count(s) from EmployeeSchedule s where s.businessId = :bid", businessId);
            assertZero("select count(p) from EmployeePerformance p where p.businessId = :bid", businessId);
            assertZero("select count(t) from EmployeeTarget t where t.businessId = :bid", businessId);
            assertZero("select count(c) from Complaint c where c.businessId = :bid", businessId);
            assertZero("select count(s) from SecurityEvent s where s.businessId = :bid", businessId);
            assertZero("select count(c) from ComplianceReport c where c.businessId = :bid", businessId);
            assertZero("select count(c) from ConsentRecord c where c.businessId = :bid", businessId);
            assertZero("select count(d) from DataSubjectRequest d where d.businessId = :bid", businessId);
            assertZero("select count(d) from DataRetentionPolicy d where d.businessId = :bid", businessId);
            assertZero("select count(a) from AuditLog a where a.businessId = :bid", businessId);
            assertZero("select count(b) from Business b where b.id = :bid", businessId);
            return null;
        });

        // global tables deliberately survive
        assertTrue(couponRepository.findByCode(couponCode).isPresent(), "global coupon must survive purge");
        assertTrue(roleRepository.findByName("BUSINESS_ADMIN").isPresent(), "global role must survive purge");
        assertTrue(permissionRepository.findByName("user.view").isPresent(), "global permission must survive purge");

        // platform admin can still authenticate
        assertNotNull(loginToken("admin", "Admin@123"));

        // owner account is erased → login fails
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of(
                                "username", created.ownerUsername(),
                                "password", created.tempPassword()))))
                .andExpect(status().isUnauthorized());

        // business row is gone
        assertProblemDetail(get("/api/v1/admin/businesses/" + businessId, adminToken()), 400, "BUSINESS_NOT_FOUND");
    }

    /** Runs inside the surrounding transaction (uses the shared EntityManager). */
    private void assertZero(String jpql, UUID businessId) {
        Number n = (Number) em.createQuery(jpql)
                .setParameter("bid", businessId)
                .getSingleResult();
        assertEquals(0L, n.longValue(), "expected 0 rows for: " + jpql);
    }

    /** One representative row in every erasure bucket; all FKs resolve in one commit. */
    private void seedFullGraph(UUID bid, String couponCode) {
        LocalDateTime now = LocalDateTime.now();
        Shop shop = shopRepository.findActiveByBusinessId(bid).get(0);
        UUID shopId = shop.getId();

        // -- global (must survive purge) --------------------------------
        Coupon coupon = Coupon.builder()
                .code(couponCode)
                .discountType(DiscountType.PERCENT)
                .discountValue(new BigDecimal("10"))
                .maxRedemptions(100)
                .redemptionsCount(0)
                .status(CouponStatus.ACTIVE)
                .startsAt(now.minusDays(1))
                .expiresAt(now.plusDays(30))
                .build();
        em.persist(coupon);

        BillingPlan plan = billingPlanRepository.findAllByStatusOrderByNameAsc(PlanStatus.ACTIVE).get(0);
        BillingPlan managedPlan = em.merge(plan);
        PlanVersion planVersion = planVersionRepository
                .findFirstByPlanOrderByEffectiveFromDesc(managedPlan).orElseThrow();
        PlanVersion managedVersion = em.merge(planVersion);

        Role adminRole = em.merge(roleRepository.findByName("BUSINESS_ADMIN").orElseThrow());
        Permission viewPerm = em.merge(permissionRepository.findByName("user.view").orElseThrow());

        // -- element collections -----------------------------------------
        shop.getOperatingHours().add(new OperatingHour(OperatingHour.DayOfWeek.MONDAY, "09:00", "17:00", false));

        // -- staff user + identity rows -----------------------------------
        User staff = new User();
        staff.setBusinessId(bid);
        staff.setUsername("staff_" + random());
        staff.setEmail("staff_" + random() + "@test.com");
        staff.setPhone("+1" + (555_000_0000L + counter.incrementAndGet()));
        staff.setFirstName("Staff");
        staff.setLastName("One");
        staff.setEnabled(true);
        staff.setPrimaryShopId(shopId);
        staff.addShop(shop);
        em.persist(staff);

        AuthAccount staffAuth = new AuthAccount();
        staffAuth.setUser(staff);
        staffAuth.setPasswordHash("$2a$10$seedonlyseedonlyseedonlyseedonlyseedonlyseedonly");
        staffAuth.setFailedAttempts(0);
        staffAuth.setPasswordLastChanged(now);
        em.persist(staffAuth);

        UserRole staffRole = new UserRole();
        staffRole.setUser(staff);
        staffRole.setRole(adminRole);
        staffRole.setBusinessId(bid);
        staffRole.setShopId(shopId);
        em.persist(staffRole);

        UserPermission staffPerm = new UserPermission();
        staffPerm.setUser(staff);
        staffPerm.setPermission(viewPerm);
        staffPerm.setBusinessId(bid);
        staffPerm.setShopId(shopId);
        staffPerm.setGrantedAt(now);
        em.persist(staffPerm);

        UserDevice device = new UserDevice();
        device.setBusinessId(bid);
        device.setUserId(staff.getId());
        device.setDeviceId("device-" + random());
        device.setDeviceType(UserDevice.DeviceType.WEB);
        device.setLastUsedAt(now);
        em.persist(device);

        UserNotificationPreference pref = new UserNotificationPreference();
        pref.setUserId(staff.getId());
        pref.setBusinessId(bid);
        pref.setChannel(Notification.NotificationChannel.IN_APP);
        pref.setNotificationType(Notification.NotificationType.ORDER_STATUS);
        pref.setEnabled(true);
        em.persist(pref);

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(staff);
        refreshToken.setTokenHash("refresh-" + random());
        refreshToken.setExpiresAt(now.plusDays(1));
        em.persist(refreshToken);

        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setUser(staff);
        resetToken.setTokenHash("reset-" + random());
        resetToken.setExpiresAt(now.plusDays(1));
        em.persist(resetToken);

        // -- customer subtree ---------------------------------------------
        Customer customer = new Customer();
        customer.setBusinessId(bid);
        customer.setFirstName("Cust");
        customer.setLastName("One");
        customer.setPhone("+1" + (555_000_0000L + counter.incrementAndGet()));
        customer.setEmail("cust_" + random() + "@test.com");
        em.persist(customer);

        CustomerAddress address = new CustomerAddress();
        address.setCustomer(customer);
        address.setType("HOME");
        address.setAddressLine1("1 Main St");
        address.setCity("Springfield");
        em.persist(address);

        CustomerNote customerNote = new CustomerNote();
        customerNote.setCustomer(customer);
        customerNote.setContent("Prefers morning pickup");
        em.persist(customerNote);

        CustomerPreferences prefs = new CustomerPreferences();
        prefs.setCustomer(customer);
        prefs.setDeliveryInstructions("Call on arrival");
        em.persist(prefs);

        LoyaltyTransaction loyaltyTxn = new LoyaltyTransaction();
        loyaltyTxn.setCustomer(customer);
        loyaltyTxn.setPoints(10);
        loyaltyTxn.setBalance(10);
        loyaltyTxn.setSource("ORDER");
        em.persist(loyaltyTxn);

        CorporateAccount corporate = new CorporateAccount();
        corporate.setBusinessId(bid);
        corporate.setCustomerId(customer.getId());
        corporate.setCompanyName("Acme Corp");
        corporate.setCreditLimit(new BigDecimal("10000.00"));
        em.persist(corporate);

        LoyaltyTier loyaltyTier = new LoyaltyTier();
        loyaltyTier.setBusinessId(bid);
        loyaltyTier.setName("Bronze");
        loyaltyTier.setLevel(1);
        loyaltyTier.setPointsRequired(0);
        em.persist(loyaltyTier);

        // -- catalogue ------------------------------------------------------
        GarmentType garmentType = new GarmentType();
        garmentType.setBusinessId(bid);
        garmentType.setName("Shirt");
        garmentType.setIsActive(true);
        em.persist(garmentType);

        ServiceType serviceType = new ServiceType();
        serviceType.setBusinessId(bid);
        serviceType.setName("Wash & Fold");
        serviceType.setIsActive(true);
        em.persist(serviceType);

        ServiceGarmentPricing pricing = new ServiceGarmentPricing();
        pricing.setBusinessId(bid);
        pricing.setServiceTypeId(serviceType.getId());
        pricing.setGarmentTypeId(garmentType.getId());
        pricing.setPrice(new BigDecimal("5.50"));
        pricing.setIsActive(true);
        em.persist(pricing);

        // -- order graph ----------------------------------------------------
        Order order = new Order();
        order.setBusinessId(bid);
        order.setShopId(shopId);
        order.setOrderNumber("ORD-" + random());
        order.setTrackingNumber("TRK-" + random());
        order.setCustomerId(customer.getId());
        order.setStatus(Order.OrderStatus.COMPLETED);
        order.setItemCount(1);
        order.setTotalAmount(new BigDecimal("107.50"));
        order.setPaidAmount(new BigDecimal("107.50"));
        order.setBalanceDue(BigDecimal.ZERO);
        order.setReceivedAt(now);
        order.getTags().add("PRIORITY");
        em.persist(order);

        OrderItem orderItem = new OrderItem();
        orderItem.setOrder(order);
        orderItem.setItemNumber("1");
        orderItem.setServiceType("WASH_FOLD");
        orderItem.setQuantity(2);
        orderItem.setUnitPrice(new BigDecimal("5.50"));
        orderItem.setSubtotal(new BigDecimal("11.00"));
        orderItem.setTotal(new BigDecimal("11.00"));
        orderItem.setStatus(OrderItem.ItemStatus.COMPLETED);
        orderItem.getImages().add("item-1.png");
        em.persist(orderItem);

        OrderItemUnit unit = new OrderItemUnit();
        unit.setOrderItem(orderItem);
        unit.setUnitNumber(1);
        unit.setBarcode("BAR-" + random());
        em.persist(unit);

        ItemStatusHistory statusHistory = new ItemStatusHistory();
        statusHistory.setOrderItem(orderItem);
        statusHistory.setStatus("COMPLETED");
        statusHistory.setTimestamp(now);
        em.persist(statusHistory);

        OrderTimeline timeline = new OrderTimeline();
        timeline.setOrder(order);
        timeline.setType("STATUS_CHANGE");
        timeline.setStatus("COMPLETED");
        timeline.setTimestamp(now);
        em.persist(timeline);

        OrderNote orderNote = new OrderNote();
        orderNote.setOrder(order);
        orderNote.setContent("Handle with care");
        em.persist(orderNote);

        OrderDiscrepancy discrepancy = new OrderDiscrepancy();
        discrepancy.setBusinessId(bid);
        discrepancy.setOrder(order);
        discrepancy.setOrderItemId(orderItem.getId());
        discrepancy.setType(OrderDiscrepancy.DiscrepancyType.DAMAGED);
        discrepancy.setStatus(OrderDiscrepancy.DiscrepancyStatus.OPEN);
        discrepancy.setDescription("Torn sleeve");
        discrepancy.setReportedBy(staff.getId());
        em.persist(discrepancy);

        OrderPayment orderPayment = new OrderPayment();
        orderPayment.setBusinessId(bid);
        orderPayment.setOrderId(order.getId());
        orderPayment.setCustomerId(customer.getId());
        orderPayment.setAmount(new BigDecimal("107.50"));
        orderPayment.setMethod("CARD");
        orderPayment.setStatus("COMPLETED");
        orderPayment.setPaidAt(now);
        em.persist(orderPayment);

        Refund refund = new Refund();
        refund.setPayment(orderPayment);
        refund.setAmount(new BigDecimal("11.00"));
        refund.setReason("Partial damage");
        refund.setStatus("COMPLETED");
        refund.setProcessedAt(now);
        refund.setBusinessId(bid);
        em.persist(refund);

        Invoice invoice = new Invoice();
        invoice.setBusinessId(bid);
        invoice.setInvoiceNumber("INV-" + random());
        invoice.setAccountId(UUID.randomUUID());
        invoice.setCompanyName("Acme Laundry");
        invoice.setPeriodStart(LocalDate.now().minusDays(30));
        invoice.setPeriodEnd(LocalDate.now());
        invoice.setDueDate(LocalDate.now().plusDays(14));
        invoice.setSubtotal(new BigDecimal("100.00"));
        invoice.setTax(new BigDecimal("7.50"));
        invoice.setTotal(new BigDecimal("107.50"));
        invoice.setStatus("OPEN");
        em.persist(invoice);

        InvoiceItem invoiceItem = new InvoiceItem();
        invoiceItem.setInvoice(invoice);
        invoiceItem.setOrderId(order.getId());
        invoiceItem.setOrderNumber(order.getOrderNumber());
        invoiceItem.setOrderDate(now);
        invoiceItem.setAmount(new BigDecimal("107.50"));
        invoiceItem.setStatus("PENDING");
        em.persist(invoiceItem);

        // -- quality graph ---------------------------------------------------
        QualityChecklist checklist = new QualityChecklist();
        checklist.setBusinessId(bid);
        checklist.setName("Standard");
        checklist.setIsActive(true);
        em.persist(checklist);

        ChecklistItem checklistItem = new ChecklistItem();
        checklistItem.setChecklist(checklist);
        checklistItem.setDescription("No stains");
        checklistItem.setRequired(true);
        checklistItem.setItemOrder(1);
        checklistItem.setFailureSeverity("HIGH");
        em.persist(checklistItem);

        QualityCheck qualityCheck = new QualityCheck();
        qualityCheck.setBusinessId(bid);
        qualityCheck.setOrderItemId(orderItem.getId());
        qualityCheck.setChecklistId(checklist.getId());
        qualityCheck.setStatus("PASSED");
        qualityCheck.setCheckedBy(UUID.randomUUID());
        qualityCheck.setCheckedAt(now);
        em.persist(qualityCheck);

        CheckResult checkResult = new CheckResult();
        checkResult.setQualityCheck(qualityCheck);
        checkResult.setChecklistItemId(checklistItem.getId());
        checkResult.setPassed(true);
        em.persist(checkResult);

        Defect defect = new Defect();
        defect.setQualityCheck(qualityCheck);
        defect.setType("STAIN");
        defect.setSeverity("LOW");
        defect.setDescription("Small stain");
        defect.setStatus("OPEN");
        defect.setReportedAt(now);
        defect.getImages().add("defect-1.png");
        em.persist(defect);

        // -- inventory graph --------------------------------------------------
        Supplier supplier = new Supplier();
        supplier.setBusinessId(bid);
        supplier.setName("ChemCo");
        supplier.setIsActive(true);
        supplier.getCategories().add("CHEMICALS");
        em.persist(supplier);

        InventoryItem item = new InventoryItem();
        item.setBusinessId(bid);
        item.setSku("SKU-" + random());
        item.setName("Detergent 5L");
        item.setCategory(InventoryItem.ItemCategory.DETERGENT);
        item.setUnit(InventoryItem.UnitOfMeasure.LITER);
        item.setCurrentStock(new BigDecimal("20"));
        item.setReorderLevel(10);
        item.setReorderQuantity(15);
        em.persist(item);

        ShopStock shopStock = new ShopStock();
        shopStock.setBusinessId(bid);
        shopStock.setShopId(shopId);
        shopStock.setItem(item);
        shopStock.setQuantity(new BigDecimal("8"));
        em.persist(shopStock);

        StockAlert stockAlert = new StockAlert();
        stockAlert.setBusinessId(bid);
        stockAlert.setShopId(shopId);
        stockAlert.setItem(item);
        stockAlert.setCurrentStock(new BigDecimal("2"));
        stockAlert.setReorderLevel(5);
        stockAlert.setStatus(StockAlert.AlertStatus.ACTIVE);
        stockAlert.setSeverity(StockAlert.AlertSeverity.WARNING);
        em.persist(stockAlert);

        StockTransaction stockTxn = new StockTransaction();
        stockTxn.setBusinessId(bid);
        stockTxn.setShopId(shopId);
        stockTxn.setItem(item);
        stockTxn.setQuantity(new BigDecimal("5"));
        stockTxn.setType(StockTransaction.TransactionType.RECEIVED);
        stockTxn.setReason("Restock");
        stockTxn.setPerformedBy(staff.getId());
        stockTxn.setBeforeQuantity(new BigDecimal("3"));
        stockTxn.setAfterQuantity(new BigDecimal("8"));
        stockTxn.setTransactionDate(now);
        em.persist(stockTxn);

        StockRequest stockRequest = new StockRequest();
        stockRequest.setBusinessId(bid);
        stockRequest.setShopId(shopId);
        stockRequest.setItem(item);
        stockRequest.setRequestedBy(UUID.randomUUID());
        stockRequest.setQuantity(new BigDecimal("10"));
        stockRequest.setUrgency(StockRequest.Urgency.HIGH);
        stockRequest.setStatus(StockRequest.RequestStatus.PENDING);
        em.persist(stockRequest);

        PurchaseOrder purchaseOrder = new PurchaseOrder();
        purchaseOrder.setBusinessId(bid);
        purchaseOrder.setShopId(shopId);
        purchaseOrder.setPoNumber("PO-" + random());
        purchaseOrder.setSupplier(supplier);
        purchaseOrder.setStatus(PurchaseOrder.PurchaseOrderStatus.DRAFT);
        purchaseOrder.setOrderDate(now);
        purchaseOrder.setTotal(new BigDecimal("250.00"));
        em.persist(purchaseOrder);

        PurchaseOrderItem poItem = new PurchaseOrderItem();
        poItem.setPurchaseOrder(purchaseOrder);
        poItem.setItem(item);
        poItem.setQuantity(10);
        poItem.setUnitPrice(new BigDecimal("25.00"));
        poItem.setTotal(new BigDecimal("250.00"));
        em.persist(poItem);

        // -- billing tree -------------------------------------------------------
        Subscription subscription = Subscription.builder()
                .businessId(bid)
                .plan(managedPlan)
                .planVersion(managedVersion)
                .status(SubscriptionStatus.ACTIVE)
                .currentPeriodStart(now.minusDays(10))
                .currentPeriodEnd(now.plusDays(20))
                .trialEndsAt(now.plusDays(4))
                .cancelAtPeriodEnd(false)
                .retryCount(0)
                .build();
        em.persist(subscription);
        UUID subId = subscription.getId();

        SubscriptionFeature feature = SubscriptionFeature.builder()
                .subscription(subscription)
                .featureKey("max_shops")
                .value("1")
                .overriddenAt(now)
                .build();
        em.persist(feature);

        SubscriptionCoupon subCoupon = SubscriptionCoupon.builder()
                .id(new SubscriptionCouponId(subId, coupon.getId()))
                .subscription(subscription)
                .coupon(coupon)
                .build();
        em.persist(subCoupon);

        SubscriptionEvent subEvent = SubscriptionEvent.builder()
                .subscriptionId(subId)
                .fromStatus(SubscriptionStatus.TRIALING)
                .toStatus(SubscriptionStatus.ACTIVE)
                .reason("seed")
                .occurredAt(now)
                .build();
        em.persist(subEvent);

        UsageRecord usage = UsageRecord.builder()
                .subscriptionId(subId)
                .featureKey("orders")
                .quantity(new BigDecimal("5"))
                .recordedAt(now)
                .build();
        em.persist(usage);

        BillingInvoice billingInvoice = BillingInvoice.builder()
                .subscriptionId(subId)
                .invoiceNumber("BILL-" + random())
                .idempotencyKey("idem-" + random())
                .amount(new BigDecimal("29.00"))
                .status(InvoiceStatus.OPEN)
                .issuedAt(now.minusDays(5))
                .dueAt(now.plusDays(9))
                .build();
        em.persist(billingInvoice);

        InvoiceLineItem lineItem = InvoiceLineItem.builder()
                .invoice(billingInvoice)
                .type(LineItemType.SUBSCRIPTION)
                .description("Monthly plan")
                .amount(new BigDecimal("29.00"))
                .periodStart(now.minusDays(30))
                .periodEnd(now)
                .build();
        em.persist(lineItem);

        BillingPayment billingPayment = BillingPayment.builder()
                .invoiceId(billingInvoice.getId())
                .idempotencyKey("idem-" + random())
                .gateway("STRIPE")
                .gatewayTransactionId("pi_" + random())
                .amount(new BigDecimal("29.00"))
                .feeAmount(BigDecimal.ZERO)
                .netAmount(new BigDecimal("29.00"))
                .status(PaymentStatus.APPROVED)
                .paidAt(now)
                .build();
        em.persist(billingPayment);

        CouponRedemption redemption = CouponRedemption.builder()
                .couponId(coupon.getId())
                .businessId(bid)
                .subscriptionId(subId)
                .redeemedAt(now)
                .build();
        em.persist(redemption);

        PaymentReconciliationItem reconciliation = new PaymentReconciliationItem();
        reconciliation.setProvider("STRIPE");
        reconciliation.setProviderReference("py_" + random());
        reconciliation.setAmount(new BigDecimal("29.00"));
        reconciliation.setReceivedAt(now);
        reconciliation.setStatus("MATCHED");
        reconciliation.setBusinessId(bid);
        reconciliation.setOrderId(order.getId());
        em.persist(reconciliation);

        BillingPaymentMethod paymentMethod = BillingPaymentMethod.builder()
                .businessId(bid)
                .type(PaymentMethodType.CARD)
                .isDefault(true)
                .gatewayCustomerId("cus_" + random())
                .gatewayMethodId("pm_" + random())
                .label("Visa •• 4242")
                .build();
        em.persist(paymentMethod);

        // -- notification graph ------------------------------------------------
        NotificationTemplate template = new NotificationTemplate();
        template.setBusinessId(bid);
        template.setName("Order status");
        template.setType(Notification.NotificationType.ORDER_STATUS);
        template.setChannel(Notification.NotificationChannel.IN_APP);
        template.setTitleTemplate("Order {{orderNumber}}");
        template.setBodyTemplate("Your order is {{status}}");
        template.setIsActive(true);
        em.persist(template);

        Notification notification = new Notification();
        notification.setBusinessId(bid);
        notification.setUserId(staff.getId());
        notification.setTemplateId(template.getId());
        notification.setType(Notification.NotificationType.ORDER_STATUS);
        notification.setChannel(Notification.NotificationChannel.IN_APP);
        notification.setTitle("Order ready");
        notification.setBody("Order ORD-1 is ready");
        notification.setStatus(Notification.NotificationStatus.SENT);
        notification.setPriority(Notification.NotificationPriority.NORMAL);
        notification.setSentAt(now);
        em.persist(notification);

        NotificationLog logEntry = new NotificationLog();
        logEntry.setBusinessId(bid);
        logEntry.setNotification(notification);
        logEntry.setRecipient(staff.getEmail());
        logEntry.setChannel(Notification.NotificationChannel.IN_APP);
        logEntry.setStatus(NotificationLog.DeliveryStatus.SENT);
        logEntry.setSentAt(now);
        em.persist(logEntry);

        NotificationDelivery delivery = new NotificationDelivery();
        delivery.setBusinessId(bid);
        delivery.setNotificationId(notification.getId());
        delivery.setChannel(Notification.NotificationChannel.IN_APP);
        delivery.setRecipient(staff.getEmail());
        delivery.setStatus(NotificationDeliveryStatus.SENT);
        delivery.setSentAt(now);
        em.persist(delivery);

        NotificationDeliveryEvent deliveryEvent = new NotificationDeliveryEvent();
        deliveryEvent.setBusinessId(bid);
        deliveryEvent.setDeliveryId(delivery.getId());
        deliveryEvent.setEventType(NotificationDeliveryEventType.SENT);
        deliveryEvent.setOccurredAt(now);
        em.persist(deliveryEvent);

        NotificationOutbox outbox = new NotificationOutbox();
        outbox.setBusinessId(bid);
        outbox.setUserId(staff.getId());
        outbox.setTemplateId(template.getId());
        outbox.setType(Notification.NotificationType.ORDER_STATUS);
        outbox.setChannel(Notification.NotificationChannel.IN_APP);
        outbox.setTitle("Pending notice");
        outbox.setBody("Still pending");
        outbox.setStatus(NotificationOutboxStatus.PENDING);
        em.persist(outbox);

        EmailConfiguration emailConfig = new EmailConfiguration();
        emailConfig.setBusinessId(bid);
        emailConfig.setHost("smtp.example.com");
        emailConfig.setPort(587);
        emailConfig.setFromAddress("no-reply@example.com");
        emailConfig.setIsConfigured(false);
        em.persist(emailConfig);

        SMSConfiguration smsConfig = new SMSConfiguration();
        smsConfig.setBusinessId(bid);
        smsConfig.setProvider(SMSConfiguration.SMSProvider.TWILIO);
        smsConfig.setFromNumber("+15551234567");
        smsConfig.setIsConfigured(false);
        em.persist(smsConfig);

        PushNotificationConfiguration pushConfig = new PushNotificationConfiguration();
        pushConfig.setBusinessId(bid);
        pushConfig.setApnsBundleId("com.example.app");
        pushConfig.setIsConfigured(false);
        em.persist(pushConfig);

        // -- payments misc -------------------------------------------------------
        CashDrawerSession drawer = new CashDrawerSession();
        drawer.setBusinessId(bid);
        drawer.setShopId(shopId);
        drawer.setOpenedBy(staff.getId());
        drawer.setOpenedAt(now);
        drawer.setStartingCash(new BigDecimal("100.00"));
        drawer.setStatus("OPEN");
        em.persist(drawer);

        PaymentMethod paymentMethodType = new PaymentMethod();
        paymentMethodType.setBusinessId(bid);
        paymentMethodType.setName("Cash");
        paymentMethodType.setType("CASH");
        paymentMethodType.setIsActive(true);
        em.persist(paymentMethodType);

        PaymentProviderConfig providerConfig = new PaymentProviderConfig();
        providerConfig.setBusinessId(bid);
        providerConfig.setProvider("STRIPE");
        providerConfig.setEnabled(false);
        providerConfig.setConnectionMode(ProviderConnectionMode.DISCONNECTED);
        em.persist(providerConfig);

        // -- employee + expense ----------------------------------------------------
        Expense expense = new Expense();
        expense.setBusinessId(bid);
        expense.setCategory(ExpenseCategory.SUPPLIES);
        expense.setDescription("Detergent restock");
        expense.setAmount(new BigDecimal("125.00"));
        expense.setExpenseDate(LocalDate.now());
        expense.setCreatedBy(staff.getId());
        em.persist(expense);

        Attendance attendance = new Attendance();
        attendance.setBusinessId(bid);
        attendance.setEmployeeId(staff.getId());
        attendance.setDate(LocalDate.now());
        attendance.setStatus(Attendance.AttendanceStatus.PRESENT);
        attendance.setCheckInTime(now);
        em.persist(attendance);

        TimeEntry timeEntry = new TimeEntry();
        timeEntry.setBusinessId(bid);
        timeEntry.setEmployeeId(staff.getId());
        timeEntry.setShopId(shopId);
        timeEntry.setEventType(TimeEntry.EventType.CLOCK_IN);
        timeEntry.setTimestamp(now);
        em.persist(timeEntry);

        EmployeeShift shift = new EmployeeShift();
        shift.setBusinessId(bid);
        shift.setEmployeeId(staff.getId());
        shift.setShopId(shopId);
        shift.setDate(LocalDate.now());
        shift.setScheduledStart(now);
        shift.setScheduledEnd(now.plusHours(8));
        shift.setStatus(EmployeeShift.ShiftStatus.COMPLETED);
        em.persist(shift);

        EmployeeSchedule schedule = new EmployeeSchedule();
        schedule.setBusinessId(bid);
        schedule.setEmployeeId(staff.getId());
        schedule.setShopId(shopId);
        schedule.setDate(LocalDate.now());
        schedule.setDayOfWeek(java.time.DayOfWeek.MONDAY);
        schedule.setStartTime(LocalTime.of(9, 0));
        schedule.setEndTime(LocalTime.of(17, 0));
        schedule.setIsActive(true);
        em.persist(schedule);

        EmployeePerformance performance = new EmployeePerformance();
        performance.setBusinessId(bid);
        performance.setEmployeeId(staff.getId());
        performance.setPeriodStart(LocalDate.now().minusDays(7));
        performance.setPeriodEnd(LocalDate.now());
        em.persist(performance);

        EmployeeTarget target = new EmployeeTarget();
        target.setBusinessId(bid);
        target.setEmployeeId(staff.getId());
        target.setDate(LocalDate.now());
        target.setMetric(EmployeeTarget.TargetMetric.ORDERS);
        target.setTargetValue(10);
        em.persist(target);

        // -- audit / compliance / complaints -----------------------------------------
        AuditLog auditLog = AuditLog.builder()
                .timestamp(now)
                .businessId(bid)
                .shopId(shopId)
                .entityType("Business")
                .entityId(bid)
                .action("PURGE")
                .category("LIFECYCLE")
                .severity(AuditLog.AuditSeverity.INFO)
                .build();
        em.persist(auditLog);

        SecurityEvent securityEvent = new SecurityEvent();
        securityEvent.setBusinessId(bid);
        securityEvent.setTimestamp(now);
        securityEvent.setEventType(SecurityEvent.SecurityEventType.LOGIN_SUCCESS);
        securityEvent.setSeverity(AuditLog.AuditSeverity.INFO);
        securityEvent.setUserId(staff.getId());
        securityEvent.setUsername(staff.getUsername());
        em.persist(securityEvent);

        ComplianceReport complianceReport = new ComplianceReport();
        complianceReport.setBusinessId(bid);
        complianceReport.setReportNumber("RPT-" + random());
        complianceReport.setReportType("GDPR");
        complianceReport.setGeneratedAt(now);
        complianceReport.setStatus("GENERATED");
        em.persist(complianceReport);

        ConsentRecord consent = new ConsentRecord();
        consent.setBusinessId(bid);
        consent.setCustomerId(customer.getId());
        consent.setConsentType(ConsentRecord.ConsentType.MARKETING);
        consent.setGranted(true);
        consent.setGrantedAt(now);
        consent.setStatus(ConsentRecord.ConsentStatus.ACTIVE);
        em.persist(consent);

        DataSubjectRequest dsr = new DataSubjectRequest();
        dsr.setBusinessId(bid);
        dsr.setRequestNumber("DSR-" + random());
        dsr.setCustomerId(customer.getId());
        dsr.setCustomerName("Cust One");
        dsr.setRequestType(DataSubjectRequest.RequestType.ACCESS);
        dsr.setStatus(DataSubjectRequest.RequestStatus.SUBMITTED);
        dsr.setSubmittedAt(now);
        dsr.setDueDate(now.plusDays(30));
        em.persist(dsr);

        DataRetentionPolicy retention = new DataRetentionPolicy();
        retention.setBusinessId(bid);
        retention.setEntityType("AUDIT_LOG");
        retention.setRetentionDays(365);
        retention.setIsActive(true);
        em.persist(retention);

        Complaint complaint = new Complaint();
        complaint.setBusinessId(bid);
        complaint.setComplaintNumber("CMP-" + random());
        complaint.setCustomerName("Cust One");
        complaint.setCategory(ComplaintCategory.QUALITY);
        complaint.setSeverity(ComplaintSeverity.LOW);
        complaint.setDescription("Minor stain on dress");
        complaint.setStatus(ComplaintStatus.OPEN);
        complaint.setSlaDueAt(now.plusDays(3));
        em.persist(complaint);

        BusinessConfig config = new BusinessConfig();
        config.setBusinessId(bid);
        config.setTaxRate(new BigDecimal("7.50"));
        em.persist(config);
    }
}