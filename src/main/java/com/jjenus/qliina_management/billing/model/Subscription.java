package com.jjenus.qliina_management.billing.model;

import com.jjenus.qliina_management.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * The hub of the billing schema (MD §2 subscriptions). Business-centric: a
 * business owns exactly one live subscription. The subscription locks onto a
 * {@link PlanVersion} at signup so price changes grandfather existing cohorts.
 */
@Entity
@Table(name = "billing_subscriptions", indexes = {
    @Index(name = "idx_billing_subs_business", columnList = "business_id"),
    @Index(name = "idx_billing_subs_status", columnList = "status"),
    @Index(name = "idx_billing_subs_period_end", columnList = "current_period_end"),
    @Index(name = "idx_billing_subs_next_retry", columnList = "next_retry_at")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Subscription extends BaseEntity {

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private BillingPlan plan;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_version_id", nullable = false)
    private PlanVersion planVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SubscriptionStatus status;

    @Column(name = "current_period_start")
    private LocalDateTime currentPeriodStart;

    @Column(name = "current_period_end")
    private LocalDateTime currentPeriodEnd;

    @Column(name = "trial_ends_at")
    private LocalDateTime trialEndsAt;

    @Column(name = "cancel_at_period_end", nullable = false)
    private boolean cancelAtPeriodEnd;

    /** Dunning retries — only meaningful in PAST_DUE (§6). */
    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    /** Scheduled (deferred) downgrade — §7. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pending_plan_id")
    private BillingPlan pendingPlan;

    @Column(name = "pending_change_at")
    private LocalDateTime pendingChangeAt;
}
