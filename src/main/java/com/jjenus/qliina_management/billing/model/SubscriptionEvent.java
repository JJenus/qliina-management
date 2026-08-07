package com.jjenus.qliina_management.billing.model;

import com.jjenus.qliina_management.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Append-only status transition record (MD §2 subscription_events). Support and
 * analytics query this, not the current status column alone.
 */
@Entity
@Table(name = "billing_subscription_events", indexes = {
    @Index(name = "idx_billing_sub_events_sub", columnList = "subscription_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionEvent extends BaseEntity {

    @Column(name = "subscription_id", nullable = false)
    private UUID subscriptionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 20)
    private SubscriptionStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 20)
    private SubscriptionStatus toStatus;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;
}
