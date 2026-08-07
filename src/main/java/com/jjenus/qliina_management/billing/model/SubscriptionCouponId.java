package com.jjenus.qliina_management.billing.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

/**
 * Composite key for {@link SubscriptionCoupon} (MD §2 subscription_coupons).
 */
@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionCouponId implements Serializable {

    @Column(name = "subscription_id", nullable = false)
    private UUID subscriptionId;

    @Column(name = "coupon_id", nullable = false)
    private UUID couponId;
}
