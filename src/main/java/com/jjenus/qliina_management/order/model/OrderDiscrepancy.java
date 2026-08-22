// ./src/main/java/com/jjenus/qliina_management/order/model/OrderDiscrepancy.java
package com.jjenus.qliina_management.order.model;

import com.jjenus.qliina_management.common.BaseTenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A problem reported by a worker about an order, routed to front desk /
 * managerial roles for resolution.
 * Lifecycle: OPEN → ACKNOWLEDGED → RESOLVED | DISMISSED.
 */
@Entity
@Table(name = "order_discrepancies", indexes = {
    @Index(name = "idx_discrepancies_business_status", columnList = "business_id, status"),
    @Index(name = "idx_discrepancies_order", columnList = "order_id"),
    @Index(name = "idx_discrepancies_reported_by", columnList = "reported_by"),
    @Index(name = "idx_discrepancies_created", columnList = "created_at")
})
@Getter
@Setter
public class OrderDiscrepancy extends BaseTenantEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    /** Specific item the discrepancy is about (null = order-level). */
    @Column(name = "order_item_id")
    private UUID orderItemId;

    @Column(name = "reported_by", nullable = false)
    private UUID reportedBy;

    @Column(name = "type", nullable = false)
    @Enumerated(EnumType.STRING)
    private DiscrepancyType type;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private DiscrepancyStatus status;

    @Column(name = "handled_by")
    private UUID handledBy;

    @Column(name = "handling_note", columnDefinition = "TEXT")
    private String handlingNote;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    public enum DiscrepancyType {
        CODE_MISMATCH,   // Label / code on the garment doesn't match the system
        COUNT_MISMATCH,  // Piece count differs from what's recorded
        DAMAGED,         // Item damaged or soiled beyond the recorded state
        MISSING_ITEM,    // A recorded piece is physically missing
        OTHER
    }

    public enum DiscrepancyStatus {
        OPEN, ACKNOWLEDGED, RESOLVED, DISMISSED
    }
}
