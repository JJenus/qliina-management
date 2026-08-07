package com.jjenus.qliina_management.billing.model;

import com.jjenus.qliina_management.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Per-subscription feature override (MD §2 subscription_features). When support
 * asks "why can this customer only add 5 seats", this is what you query.
 */
@Entity
@Table(name = "billing_subscription_features", uniqueConstraints = {
    @UniqueConstraint(name = "uq_billing_subscription_feature", columnNames = {"subscription_id", "feature_key"})
}, indexes = {
    @Index(name = "idx_billing_sub_features_sub", columnList = "subscription_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionFeature extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subscription_id", nullable = false)
    private Subscription subscription;

    @Column(name = "feature_key", nullable = false, length = 64)
    private String featureKey;

    @Column(name = "feature_value", nullable = false, length = 255)
    private String value;

    @Column(name = "overridden_at")
    private LocalDateTime overriddenAt;
}
