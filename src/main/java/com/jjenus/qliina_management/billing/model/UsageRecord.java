package com.jjenus.qliina_management.billing.model;

import com.jjenus.qliina_management.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Metered usage (MD §2 usage_records) for metered billing features, e.g.
 * api_calls, storage_gb. Aggregated per subscription + feature + period.
 */
@Entity
@Table(name = "billing_usage_records", indexes = {
    @Index(name = "idx_billing_usage_sub_key", columnList = "subscription_id,feature_key,recorded_at")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsageRecord extends BaseEntity {

    @Column(name = "subscription_id", nullable = false)
    private UUID subscriptionId;

    @Column(name = "feature_key", nullable = false, length = 64)
    private String featureKey;

    @Column(name = "quantity", precision = 12, scale = 2, nullable = false)
    private BigDecimal quantity;

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;
}
