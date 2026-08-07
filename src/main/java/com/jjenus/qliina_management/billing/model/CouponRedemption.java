package com.jjenus.qliina_management.billing.model;

import com.jjenus.qliina_management.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * The coupon redemption ledger (MD §2 coupon_redemptions). Written in the same
 * transaction as the subscription/invoice so a failed payment never burns a
 * redemption (§4).
 */
@Entity
@Table(name = "billing_coupon_redemptions", indexes = {
    @Index(name = "idx_billing_coupon_red_coupon", columnList = "coupon_id,business_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CouponRedemption extends BaseEntity {

    @Column(name = "coupon_id", nullable = false)
    private UUID couponId;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "subscription_id")
    private UUID subscriptionId;

    @Column(name = "redeemed_at", nullable = false)
    private LocalDateTime redeemedAt;
}
