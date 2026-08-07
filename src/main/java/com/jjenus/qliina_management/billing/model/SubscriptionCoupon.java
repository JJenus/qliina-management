package com.jjenus.qliina_management.billing.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Junction table linking subscriptions to applied coupons (MD §2).
 * The {@link CouponRedemption} ledger — not this row — is the audit source of truth.
 */
@Entity
@Table(name = "billing_subscription_coupons", indexes = {
    @Index(name = "idx_billing_sub_coupons_coupon", columnList = "coupon_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionCoupon {

    @EmbeddedId
    private SubscriptionCouponId id;

    @MapsId("subscriptionId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subscription_id", nullable = false)
    private Subscription subscription;

    @MapsId("couponId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "coupon_id", nullable = false)
    private Coupon coupon;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
