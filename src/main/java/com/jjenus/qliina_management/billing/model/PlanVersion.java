package com.jjenus.qliina_management.billing.model;

import com.jjenus.qliina_management.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A priced snapshot of a plan (MD §2 plan_versions). Subscriptions lock onto a
 * version so a price change never silently moves existing subscribers.
 */
@Entity
@Table(name = "billing_plan_versions", uniqueConstraints = {
    @UniqueConstraint(name = "uq_billing_plan_version", columnNames = {"plan_id", "effective_from"})
}, indexes = {
    @Index(name = "idx_billing_plan_versions_plan", columnList = "plan_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanVersion extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private BillingPlan plan;

    @Column(name = "price", precision = 12, scale = 2, nullable = false)
    private BigDecimal price;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "NGN";

    @Column(name = "effective_from", nullable = false)
    private LocalDateTime effectiveFrom;
}
