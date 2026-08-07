package com.jjenus.qliina_management.billing.service;

import com.jjenus.qliina_management.billing.model.*;
import com.jjenus.qliina_management.billing.repository.*;
import com.jjenus.qliina_management.common.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Coupon validation and redemption (MD §4).
 *
 * <p>Validation order at redemption time:
 * <ol>
 *   <li>status = active</li>
 *   <li>now() between starts_at and expires_at (null = open-ended)</li>
 *   <li>not already applied to this subscription</li>
 *   <li>per-business redemption count &lt; max_redemptions_per_business</li>
 *   <li>redemptions_count &lt; max_redemptions — enforced by an ATOMIC
 *       conditional UPDATE, never a read-then-write race</li>
 * </ol>
 *
 * The counter increment and the {@link CouponRedemption} ledger row happen in
 * the same transaction as the subscription/invoice, so a failed payment never
 * burns a redemption.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CouponService {

    private final CouponRepository couponRepository;
    private final CouponRedemptionRepository redemptionRepository;
    private final SubscriptionCouponRepository subscriptionCouponRepository;
    private final SubscriptionRepository subscriptionRepository;

    /** Lookup that surfaces WHY a coupon is not redeemable (for the UI). */
    @Transactional(readOnly = true)
    public Coupon validateForLookup(String code) {
        Coupon coupon = couponRepository.findByCode(code.toUpperCase())
                .orElseThrow(() -> new BusinessException("Coupon not found", "COUPON_NOT_FOUND"));
        LocalDateTime now = LocalDateTime.now();
        if (coupon.getStatus() != CouponStatus.ACTIVE) {
            throw new BusinessException("Coupon is not active", "COUPON_NOT_ACTIVE");
        }
        if (coupon.getStartsAt() != null && now.isBefore(coupon.getStartsAt())) {
            throw new BusinessException("Coupon has not started yet", "COUPON_NOT_STARTED");
        }
        if (coupon.getExpiresAt() != null && now.isAfter(coupon.getExpiresAt())) {
            throw new BusinessException("Coupon has expired", "COUPON_EXPIRED");
        }
        return coupon;
    }

    /**
     * Redeems a coupon for a subscription. Atomic against concurrent redemptions
     * (§4). Caller must be in a transaction.
     */
    @Transactional
    public CouponRedemption redeem(UUID businessId, UUID subscriptionId, String code) {
        Coupon coupon = validateForLookup(code);
        if (subscriptionCouponRepository.existsByIdSubscriptionId(subscriptionId)) {
            throw new BusinessException("Coupon already applied to this subscription", "COUPON_ALREADY_APPLIED");
        }
        if (coupon.getMaxRedemptionsPerBusiness() != null) {
            long used = redemptionRepository.countByCouponIdAndBusinessId(coupon.getId(), businessId);
            if (used >= coupon.getMaxRedemptionsPerBusiness()) {
                throw new BusinessException("Coupon already used by this business", "COUPON_LIMIT_REACHED");
            }
        }

        // Atomic guard — no row updated means the coupon is exhausted (§4).
        int updated = couponRepository.incrementRedemptionsIfAvailable(coupon.getId(), LocalDateTime.now());
        if (updated == 0) {
            throw new BusinessException("Coupon has reached its redemption limit", "COUPON_EXHAUSTED");
        }

        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new BusinessException("Subscription not found", "SUBSCRIPTION_NOT_FOUND"));

        subscriptionCouponRepository.save(SubscriptionCoupon.builder()
                .id(new SubscriptionCouponId(subscriptionId, coupon.getId()))
                .subscription(subscription)
                .coupon(coupon)
                .build());

        CouponRedemption redemption = CouponRedemption.builder()
                .couponId(coupon.getId())
                .businessId(businessId)
                .subscriptionId(subscriptionId)
                .redeemedAt(LocalDateTime.now())
                .build();
        CouponRedemption saved = redemptionRepository.save(redemption);
        log.info("Coupon {} redeemed by business {} on subscription {}",
                coupon.getCode(), businessId, subscriptionId);
        return saved;
    }

    // ---------------------------------------------------------------------
    // Admin
    // ---------------------------------------------------------------------

    @Transactional
    public Coupon create(Coupon coupon) {
        coupon.setCode(coupon.getCode().toUpperCase());
        coupon.setRedemptionsCount(0);
        coupon.setStatus(CouponStatus.ACTIVE);
        if (couponRepository.existsByCode(coupon.getCode())) {
            throw new BusinessException("A coupon with this code already exists", "COUPON_EXISTS", "code");
        }
        return couponRepository.save(coupon);
    }

    @Transactional
    public Coupon update(UUID id, Coupon update) {
        Coupon coupon = couponRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Coupon not found", "COUPON_NOT_FOUND"));
        if (update.getDiscountType() != null) coupon.setDiscountType(update.getDiscountType());
        if (update.getDiscountValue() != null) coupon.setDiscountValue(update.getDiscountValue());
        if (update.getMaxRedemptions() != null) coupon.setMaxRedemptions(update.getMaxRedemptions());
        if (update.getMaxRedemptionsPerBusiness() != null) coupon.setMaxRedemptionsPerBusiness(update.getMaxRedemptionsPerBusiness());
        if (update.getStartsAt() != null) coupon.setStartsAt(update.getStartsAt());
        if (update.getExpiresAt() != null) coupon.setExpiresAt(update.getExpiresAt());
        if (update.getStatus() != null) coupon.setStatus(update.getStatus());
        return couponRepository.save(coupon);
    }
}
