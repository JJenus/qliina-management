// ./src/main/java/com/jjenus/qliina_management/inventory/model/StockRequest.java
package com.jjenus.qliina_management.inventory.model;

import com.jjenus.qliina_management.common.BaseTenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A worker's request for restock of a supply item (detergent, soap, ...).
 * Lifecycle: PENDING → APPROVED | REJECTED (manager) → FULFILLED (stock added)
 * or CANCELLED (by requester while PENDING).
 */
@Entity
@Table(name = "stock_requests", indexes = {
    @Index(name = "idx_stock_requests_business_status", columnList = "business_id, status"),
    @Index(name = "idx_stock_requests_shop", columnList = "shop_id"),
    @Index(name = "idx_stock_requests_requested_by", columnList = "requested_by"),
    @Index(name = "idx_stock_requests_created", columnList = "created_at")
})
@Getter
@Setter
public class StockRequest extends BaseTenantEntity {

    @Column(name = "shop_id", nullable = false)
    private UUID shopId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private InventoryItem item;

    @Column(name = "requested_by", nullable = false)
    private UUID requestedBy;

    @Column(name = "quantity", nullable = false, precision = 10, scale = 2)
    private BigDecimal quantity;

    @Column(name = "urgency", nullable = false)
    @Enumerated(EnumType.STRING)
    private Urgency urgency;

    @Column(name = "notes")
    private String notes;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private RequestStatus status;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "resolution_note")
    private String resolutionNote;

    public enum Urgency {
        LOW, NORMAL, HIGH
    }

    public enum RequestStatus {
        PENDING, APPROVED, REJECTED, FULFILLED, CANCELLED
    }
}
