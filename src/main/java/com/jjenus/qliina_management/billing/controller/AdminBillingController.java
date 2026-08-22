package com.jjenus.qliina_management.billing.controller;

import com.jjenus.qliina_management.billing.dto.*;
import com.jjenus.qliina_management.billing.model.*;
import com.jjenus.qliina_management.billing.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Platform-admin billing management: plans, versions, features (MD §5), coupons
 * (MD §4), and cross-tenant subscription views.
 */
@RestController
@RequiredArgsConstructor
public class AdminBillingController {

    private static final String BILLING = "hasPermission(null, 'PLATFORM', 'platform.billing.manage')";
    private static final String PLANS = "hasPermission(null, 'PLATFORM', 'platform.plans.manage')";
    private static final String COUPONS = "hasPermission(null, 'PLATFORM', 'platform.coupons.manage')";

    private final PlanManagementService planManagementService;
    private final BillingQueryService queryService;
    private final CouponService couponService;
    private final SubscriptionService subscriptionService;
    private final com.jjenus.qliina_management.billing.repository.CouponRedemptionRepository redemptionRepository;
    private final com.jjenus.qliina_management.business.repository.BusinessRepository businessRepository;

    // ---------------------------------------------------------------------
    // Plans / versions / features
    // ---------------------------------------------------------------------

    @GetMapping("/api/v1/admin/billing/plans")
    @PreAuthorize(BILLING + " or " + PLANS)
    public ResponseEntity<List<PlanDTO>> listPlans() {
        return ResponseEntity.ok(queryService.plans());
    }

    public record CreatePlanRequest(String name, String description, BigDecimal price,
                                    String currency, LocalDateTime effectiveFrom,
                                    List<PlanFeatureDTO> features) {
    }

    @PostMapping("/api/v1/admin/billing/plans")
    @PreAuthorize(PLANS)
    public ResponseEntity<PlanDTO> createPlan(@RequestBody CreatePlanRequest body) {
        if (body == null || body.name() == null || body.name().isBlank()) {
            throw new com.jjenus.qliina_management.common.BusinessException("'name' is required", "MISSING_NAME", "name");
        }
        if (body.price() == null) {
            throw new com.jjenus.qliina_management.common.BusinessException("'price' is required", "MISSING_PRICE", "price");
        }
        List<PlanFeature> features = body.features() == null ? List.of()
                : body.features().stream().map(f -> PlanFeature.builder()
                        .featureKey(f.featureKey()).value(f.value()).isHardLimit(f.isHardLimit()).build())
                        .toList();
        BillingPlan plan = planManagementService.createPlan(
                body.name(), body.description(), body.price(), body.currency(),
                body.effectiveFrom(), features);
        return ResponseEntity.status(HttpStatus.CREATED).body(queryService.plan(plan.getId()));
    }

    public record UpdatePlanStatusRequest(PlanStatus status) {
    }

    @PatchMapping("/api/v1/admin/billing/plans/{id}/status")
    @PreAuthorize(PLANS)
    public ResponseEntity<PlanDTO> updatePlanStatus(@PathVariable UUID id,
                                                    @RequestBody UpdatePlanStatusRequest body) {
        if (body == null || body.status() == null) {
            throw new com.jjenus.qliina_management.common.BusinessException("'status' is required", "MISSING_STATUS", "status");
        }
        BillingPlan plan = planManagementService.setStatus(id, body.status());
        return ResponseEntity.ok(queryService.plan(plan.getId()));
    }

    @DeleteMapping("/api/v1/admin/billing/plans/{id}")
    @PreAuthorize(PLANS)
    public ResponseEntity<Void> deletePlan(@PathVariable UUID id) {
        planManagementService.deletePlan(id);
        return ResponseEntity.noContent().build();
    }

    public record AddVersionRequest(BigDecimal price, String currency, LocalDateTime effectiveFrom) {
    }

    @PostMapping("/api/v1/admin/billing/plans/{id}/versions")
    @PreAuthorize(PLANS)
    public ResponseEntity<PlanDTO> addVersion(@PathVariable UUID id, @RequestBody AddVersionRequest body) {
        if (body == null || body.price() == null) {
            throw new com.jjenus.qliina_management.common.BusinessException("'price' is required", "MISSING_PRICE", "price");
        }
        planManagementService.addVersion(id, body.price(), body.currency(), body.effectiveFrom());
        return ResponseEntity.ok(queryService.plan(id));
    }

    @PutMapping("/api/v1/admin/billing/plans/{id}/features")
    @PreAuthorize(PLANS)
    public ResponseEntity<PlanDTO> setFeatures(@PathVariable UUID id, @RequestBody List<PlanFeatureDTO> body) {
        List<PlanFeature> features = body == null ? List.of() : body.stream()
                .map(f -> PlanFeature.builder().featureKey(f.featureKey()).value(f.value()).isHardLimit(f.isHardLimit()).build())
                .toList();
        planManagementService.setFeatures(id, features);
        return ResponseEntity.ok(queryService.plan(id));
    }

    // ---------------------------------------------------------------------
    // Coupons
    // ---------------------------------------------------------------------

    @GetMapping("/api/v1/admin/billing/coupons")
    @PreAuthorize(BILLING + " or " + PLANS)
    public ResponseEntity<List<CouponDTO>> listCoupons() {
        return ResponseEntity.ok(queryService.coupons());
    }

    public record CreateCouponRequest(String code, DiscountType discountType, BigDecimal discountValue,
                                      Integer maxRedemptions, Integer maxRedemptionsPerBusiness,
                                      LocalDateTime startsAt, LocalDateTime expiresAt) {
    }

    @PostMapping("/api/v1/admin/billing/coupons")
    @PreAuthorize(PLANS + " or " + COUPONS)
    public ResponseEntity<CouponDTO> createCoupon(@RequestBody CreateCouponRequest body) {
        if (body == null || body.code() == null || body.code().isBlank()) {
            throw new com.jjenus.qliina_management.common.BusinessException("'code' is required", "MISSING_COUPON_CODE", "code");
        }
        if (body.discountType() == null || body.discountValue() == null) {
            throw new com.jjenus.qliina_management.common.BusinessException(
                    "'discountType' and 'discountValue' are required", "MISSING_DISCOUNT", "discountType");
        }
        Coupon coupon = couponService.create(Coupon.builder()
                .code(body.code()).discountType(body.discountType()).discountValue(body.discountValue())
                .maxRedemptions(body.maxRedemptions()).maxRedemptionsPerBusiness(body.maxRedemptionsPerBusiness())
                .startsAt(body.startsAt()).expiresAt(body.expiresAt())
                .build());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new CouponDTO(coupon.getId(), coupon.getCode(), coupon.getDiscountType(),
                        coupon.getDiscountValue(), coupon.getMaxRedemptions(), coupon.getRedemptionsCount(),
                        coupon.getMaxRedemptionsPerBusiness(), coupon.getStartsAt(), coupon.getExpiresAt(),
                        coupon.getStatus()));
    }

    @PutMapping("/api/v1/admin/billing/coupons/{id}")
    @PreAuthorize(PLANS + " or " + COUPONS)
    public ResponseEntity<CouponDTO> updateCoupon(@PathVariable UUID id, @RequestBody CreateCouponRequest body) {
        Coupon update = Coupon.builder()
                .discountType(body.discountType()).discountValue(body.discountValue())
                .maxRedemptions(body.maxRedemptions()).maxRedemptionsPerBusiness(body.maxRedemptionsPerBusiness())
                .startsAt(body.startsAt()).expiresAt(body.expiresAt())
                .build();
        Coupon coupon = couponService.update(id, update);
        return ResponseEntity.ok(new CouponDTO(coupon.getId(), coupon.getCode(), coupon.getDiscountType(),
                coupon.getDiscountValue(), coupon.getMaxRedemptions(), coupon.getRedemptionsCount(),
                coupon.getMaxRedemptionsPerBusiness(), coupon.getStartsAt(), coupon.getExpiresAt(),
                coupon.getStatus()));
    }

    @GetMapping("/api/v1/admin/billing/coupons/{id}/redemptions")
    @PreAuthorize(BILLING + " or " + COUPONS)
    public ResponseEntity<com.jjenus.qliina_management.common.PageResponse<CouponRedemptionRow>> couponRedemptions(
            @PathVariable UUID id,
            @org.springframework.data.web.PageableDefault(size = 20) org.springframework.data.domain.Pageable pageable) {
        var page = redemptionRepository.findByCouponIdOrderByRedeemedAtDesc(id, pageable);
        var bizNames = businessRepository.findAllById(
                page.getContent().stream().map(r -> r.getBusinessId()).collect(java.util.stream.Collectors.toSet()))
                .stream().collect(java.util.stream.Collectors.toMap(b -> b.getId(), b -> b.getName(), (a, b2) -> a));
        return ResponseEntity.ok(com.jjenus.qliina_management.common.PageResponse.from(
                page.map(r -> new CouponRedemptionRow(r.getId(), r.getCouponId(), r.getBusinessId(),
                        bizNames.get(r.getBusinessId()), r.getSubscriptionId(), r.getRedeemedAt()))));
    }

    public record CouponRedemptionRow(UUID id, UUID couponId, UUID businessId, String businessName,
                                      UUID subscriptionId, LocalDateTime redeemedAt) {}

    // ---------------------------------------------------------------------
    // Cross-tenant subscription views
    // ---------------------------------------------------------------------

    @GetMapping("/api/v1/admin/billing/subscriptions/{businessId}")
    @PreAuthorize(BILLING)
    public ResponseEntity<BillingSubscriptionDTO> getBusinessSubscription(@PathVariable UUID businessId) {
        return ResponseEntity.ok(queryService.subscription(businessId));
    }
}
