package com.jjenus.qliina_management.billing.model;

import com.jjenus.qliina_management.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * A feature/limit defined at plan level (MD §2 plan_features), e.g.
 * feature_key = "max_shops", value = "3", is_hard_limit = true.
 */
@Entity
@Table(name = "billing_plan_features", uniqueConstraints = {
    @UniqueConstraint(name = "uq_billing_plan_feature", columnNames = {"plan_id", "feature_key"})
}, indexes = {
    @Index(name = "idx_billing_plan_features_plan", columnList = "plan_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanFeature extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private BillingPlan plan;

    @Column(name = "feature_key", nullable = false, length = 64)
    private String featureKey;

    @Column(name = "feature_value", nullable = false, length = 255)
    private String value;

    @Column(name = "is_hard_limit", nullable = false)
    private boolean isHardLimit;
}
