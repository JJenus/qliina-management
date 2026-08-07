package com.jjenus.qliina_management.billing.model;

import com.jjenus.qliina_management.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Discount coupon (MD §2 coupons). `redemptionsCount` is a fast-read cache;
 * {@link CouponRedemption} is the real ledger for enforcement and audit (§4).
 * Never hard-deleted once redeemed.
 */
@Entity
@Table(name = "billing_coupons", indexes = {
    @Index(name = "idx_billing_coupons_status", columnList = "status")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Coupon extends BaseEntity {

    @Column(name = "code", nullable = false, unique = true, length = 40)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 20)
    private DiscountType discountType;

    @Column(name = "discount_value", precision = 12, scale = 2, nullable = false)
    private BigDecimal discountValue;

    /** null = unlimited. */
    @Column(name = "max_redemptions")
    private Integer maxRedemptions;

    @Column(name = "redemptions_count", nullable = false)
    private int redemptionsCount;

    @Column(name = "max_redemptions_per_business")
    private Integer maxRedemptionsPerBusiness;

    @Column(name = "starts_at")
    private LocalDateTime startsAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CouponStatus status;
}
