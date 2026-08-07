package com.jjenus.qliina_management.billing.controller;

import com.jjenus.qliina_management.billing.dto.*;
import com.jjenus.qliina_management.billing.model.PaymentMethodType;
import com.jjenus.qliina_management.billing.model.Subscription;
import com.jjenus.qliina_management.billing.model.UsageRecord;
import com.jjenus.qliina_management.billing.service.*;
import com.jjenus.qliina_management.common.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Self-service billing endpoints for a business.
 *
 * Accessible by business owners/admins (admin.settings), or platform staff with
 * billing / plans / audit view permissions.
 */
@RestController
@RequiredArgsConstructor
public class BillingController {

    private static final String SELF_SERVICE_ACCESS = """
        hasPermission(#businessId, 'BUSINESS', 'admin.settings')
        or hasPermission(null, 'PLATFORM', 'platform.billing.manage')
        or hasPermission(null, 'PLATFORM', 'platform.plans.manage')
        or hasPermission(null, 'PLATFORM', 'platform.audit.view')
    """;

    private final BillingQueryService queryService;
    private final SubscriptionService subscriptionService;
    private final CouponService couponService;
    private final BillingPaymentMethodService paymentMethodService;
    private final UsageService usageService;

    // ---------------------------------------------------------------------
    // Subscription overview
    // ---------------------------------------------------------------------

    @GetMapping("/api/v1/{businessId}/subscription/billing")
    @PreAuthorize(SELF_SERVICE_ACCESS)
    public ResponseEntity<BillingSubscriptionDTO> getBilling(@PathVariable UUID businessId) {
        return ResponseEntity.ok(queryService.subscription(businessId));
    }

    // ---------------------------------------------------------------------
    // Invoices
    // ---------------------------------------------------------------------

    @GetMapping("/api/v1/{businessId}/subscription/invoices")
    @PreAuthorize(SELF_SERVICE_ACCESS)
    public ResponseEntity<Page<InvoiceDTO>> listInvoices(@PathVariable UUID businessId, Pageable pageable) {
        return ResponseEntity.ok(queryService.invoices(businessId, pageable));
    }

    @GetMapping("/api/v1/{businessId}/subscription/invoices/{invoiceId}")
    @PreAuthorize(SELF_SERVICE_ACCESS)
    public ResponseEntity<InvoiceDTO> getInvoice(@PathVariable UUID businessId, @PathVariable UUID invoiceId) {
        return ResponseEntity.ok(queryService.invoice(businessId, invoiceId));
    }

    @GetMapping("/api/v1/{businessId}/subscription/invoices/{invoiceId}/payments")
    @PreAuthorize(SELF_SERVICE_ACCESS)
    public ResponseEntity<List<PaymentDTO>> getInvoicePayments(@PathVariable UUID invoiceId) {
        return ResponseEntity.ok(queryService.paymentsForInvoice(invoiceId));
    }

    // ---------------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------------

    public record CancelRequest(Boolean atPeriodEnd) {
    }

    @PostMapping("/api/v1/{businessId}/subscription/cancel")
    @PreAuthorize(SELF_SERVICE_ACCESS)
    public ResponseEntity<BillingSubscriptionDTO> cancel(@PathVariable UUID businessId,
                                                         @RequestBody(required = false) CancelRequest body) {
        boolean atPeriodEnd = body == null || body.atPeriodEnd() == null || body.atPeriodEnd();
        Subscription sub = subscriptionService.cancel(businessId, atPeriodEnd);
        return ResponseEntity.ok(queryService.subscription(sub));
    }

    public record ReactivateRequest(String plan) {
    }

    @PostMapping("/api/v1/{businessId}/subscription/reactivate")
    @PreAuthorize(SELF_SERVICE_ACCESS)
    public ResponseEntity<BillingSubscriptionDTO> reactivate(@PathVariable UUID businessId,
                                                             @RequestBody(required = false) ReactivateRequest body) {
        String plan = body != null && body.plan() != null ? body.plan() : "FREE";
        subscriptionService.reactivate(businessId, plan);
        return ResponseEntity.ok(queryService.subscription(businessId));
    }

    // ---------------------------------------------------------------------
    // Coupons
    // ---------------------------------------------------------------------

    @GetMapping("/api/v1/subscription/coupons/{code}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CouponDTO> validateCoupon(@PathVariable String code) {
        var coupon = couponService.validateForLookup(code);
        return ResponseEntity.ok(new CouponDTO(coupon.getId(), coupon.getCode(), coupon.getDiscountType(),
                coupon.getDiscountValue(), coupon.getMaxRedemptions(), coupon.getRedemptionsCount(),
                coupon.getMaxRedemptionsPerBusiness(), coupon.getStartsAt(), coupon.getExpiresAt(),
                coupon.getStatus()));
    }

    public record ApplyCouponRequest(String code) {
    }

    @PostMapping("/api/v1/{businessId}/subscription/coupons")
    @PreAuthorize(SELF_SERVICE_ACCESS)
    public ResponseEntity<BillingSubscriptionDTO> applyCoupon(@PathVariable UUID businessId,
                                                              @RequestBody ApplyCouponRequest body) {
        if (body == null || body.code() == null || body.code().isBlank()) {
            throw new BusinessException("'code' is required", "MISSING_COUPON_CODE", "code");
        }
        UUID subscriptionId = subscriptionService.getLiveSubscription(businessId).getId();
        couponService.redeem(businessId, subscriptionId, body.code());
        return ResponseEntity.ok(queryService.subscription(businessId));
    }

    // ---------------------------------------------------------------------
    // Payment methods
    // ---------------------------------------------------------------------

    @GetMapping("/api/v1/{businessId}/subscription/payment-methods")
    @PreAuthorize(SELF_SERVICE_ACCESS)
    public ResponseEntity<List<BillingPaymentMethodDTO>> listPaymentMethods(@PathVariable UUID businessId) {
        List<BillingPaymentMethodDTO> list = paymentMethodService.list(businessId).stream()
                .map(m -> new BillingPaymentMethodDTO(m.getId(), m.getBusinessId(), m.getType(),
                        m.isDefault(), m.getLabel()))
                .toList();
        return ResponseEntity.ok(list);
    }

    public record AddPaymentMethodRequest(PaymentMethodType type, String label,
                                          String gatewayCustomerId, String gatewayMethodId) {
    }

    @PostMapping("/api/v1/{businessId}/subscription/payment-methods")
    @PreAuthorize(SELF_SERVICE_ACCESS)
    public ResponseEntity<BillingPaymentMethodDTO> addPaymentMethod(@PathVariable UUID businessId,
                                                                    @RequestBody AddPaymentMethodRequest body) {
        if (body == null || body.type() == null) {
            throw new BusinessException("'type' is required", "MISSING_PAYMENT_METHOD_TYPE", "type");
        }
        var saved = paymentMethodService.add(businessId, body.type(), body.label(),
                body.gatewayCustomerId(), body.gatewayMethodId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new BillingPaymentMethodDTO(saved.getId(), saved.getBusinessId(), saved.getType(),
                        saved.isDefault(), saved.getLabel()));
    }

    @DeleteMapping("/api/v1/{businessId}/subscription/payment-methods/{id}")
    @PreAuthorize(SELF_SERVICE_ACCESS)
    public ResponseEntity<Void> removePaymentMethod(@PathVariable UUID businessId, @PathVariable UUID id) {
        paymentMethodService.remove(businessId, id);
        return ResponseEntity.noContent().build();
    }

    // ---------------------------------------------------------------------
    // Metered usage
    // ---------------------------------------------------------------------

    public record RecordUsageRequest(String featureKey, BigDecimal quantity) {
    }

    @PostMapping("/api/v1/{businessId}/subscription/usage")
    @PreAuthorize(SELF_SERVICE_ACCESS)
    public ResponseEntity<UsageRecord> recordUsage(@PathVariable UUID businessId,
                                                   @RequestBody RecordUsageRequest body) {
        if (body == null || body.featureKey() == null || body.featureKey().isBlank()) {
            throw new BusinessException("'featureKey' is required", "MISSING_FEATURE_KEY", "featureKey");
        }
        UUID subscriptionId = subscriptionService.getLiveSubscription(businessId).getId();
        return ResponseEntity.ok(usageService.record(subscriptionId, body.featureKey(), body.quantity()));
    }
}
