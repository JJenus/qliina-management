package com.jjenus.qliina_management.inventory.model;

import com.jjenus.qliina_management.common.BaseTenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "stock_transactions", indexes = {
    @Index(name = "idx_stock_txn_shop", columnList = "shop_id"),
    @Index(name = "idx_stock_txn_item", columnList = "item_id"),
    @Index(name = "idx_stock_txn_type", columnList = "type"),
    @Index(name = "idx_stock_txn_created", columnList = "created_at")
})
@Getter
@Setter
public class StockTransaction extends BaseTenantEntity {
    
    @Column(name = "shop_id", nullable = false)
    private UUID shopId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private InventoryItem item;
    
    @Column(name = "quantity", nullable = false, precision = 10, scale = 2)
    private BigDecimal quantity;
    
    @Column(name = "type", nullable = false)
    @Enumerated(EnumType.STRING)
    private TransactionType type;
    
    @Column(name = "reason", nullable = false)
    private String reason;
    
    @Column(name = "reference")
    private String reference;
    
    @Column(name = "notes")
    private String notes;
    
    @Column(name = "performed_by")
    private UUID performedBy;
    
    @Column(name = "before_quantity", precision = 10, scale = 2)
    private BigDecimal beforeQuantity;
    
    @Column(name = "after_quantity", precision = 10, scale = 2)
    private BigDecimal afterQuantity;
    
    @Column(name = "unit_cost", precision = 10, scale = 2)
    private BigDecimal unitCost;
    
    @Column(name = "total_cost", precision = 10, scale = 2)
    private BigDecimal totalCost;
    
    @Column(name = "transaction_date", nullable = false)
    private LocalDateTime transactionDate;
    
    public enum TransactionType {
        RECEIVED,        // Stock received from supplier
        USED,            // Stock used in operations
        WASTED,          // Stock wasted/damaged
        RETURNED,        // Stock returned to supplier
        TRANSFER_IN,     // Stock transferred from another shop
        TRANSFER_OUT,    // Stock transferred to another shop
        ADJUSTMENT,      // Manual stock adjustment
        COUNTED          // Stock count adjustment
    }
}
