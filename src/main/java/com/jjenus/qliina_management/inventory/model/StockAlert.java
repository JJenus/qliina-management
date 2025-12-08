package com.jjenus.qliina_management.inventory.model;

import com.jjenus.qliina_management.common.BaseTenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "stock_alerts", indexes = {
    @Index(name = "idx_alert_shop", columnList = "shop_id"),
    @Index(name = "idx_alert_item", columnList = "item_id"),
    @Index(name = "idx_alert_status", columnList = "status")
})
@Getter
@Setter
public class StockAlert extends BaseTenantEntity {
    
    @Column(name = "shop_id", nullable = false)
    private UUID shopId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private InventoryItem item;
    
    @Column(name = "current_stock", nullable = false, precision = 10, scale = 2)
    private BigDecimal currentStock;
    
    @Column(name = "reorder_level", nullable = false)
    private Integer reorderLevel;
    
    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private AlertStatus status;
    
    @Column(name = "suggested_order")
    private Integer suggestedOrder;
    
    @Column(name = "acknowledged_at")
    private LocalDateTime acknowledgedAt;
    
    @Column(name = "acknowledged_by")
    private UUID acknowledgedBy;
    
    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;
    
    @Column(name = "resolved_by")
    private UUID resolvedBy;
    
    @Column(name = "message")
    private String message;
    
    @Column(name = "severity")
    @Enumerated(EnumType.STRING)
    private AlertSeverity severity;
    
    public enum AlertStatus {
        ACTIVE, ACKNOWLEDGED, RESOLVED, IGNORED
    }
    
    public enum AlertSeverity {
        INFO, WARNING, CRITICAL
    }
}
