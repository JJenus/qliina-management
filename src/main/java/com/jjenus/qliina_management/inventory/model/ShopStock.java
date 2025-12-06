package com.jjenus.qliina_management.inventory.model;

import com.jjenus.qliina_management.common.BaseTenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "shop_stock", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"shop_id", "item_id"})
}, indexes = {
    @Index(name = "idx_shop_stock_shop", columnList = "shop_id"),
    @Index(name = "idx_shop_stock_item", columnList = "item_id"),
    @Index(name = "idx_shop_stock_status", columnList = "status")
})
@Getter
@Setter
public class ShopStock extends BaseTenantEntity {
    
    @Column(name = "shop_id", nullable = false)
    private UUID shopId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private InventoryItem item;
    
    @Column(name = "quantity", nullable = false, precision = 10, scale = 2)
    private BigDecimal quantity = BigDecimal.ZERO;
    
    @Column(name = "last_restocked")
    private LocalDateTime lastRestocked;
    
    @Column(name = "last_counted")
    private LocalDateTime lastCounted;
    
    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private StockStatus status = StockStatus.NORMAL;
    
    @Column(name = "minimum_quantity", precision = 10, scale = 2)
    private BigDecimal minimumQuantity;
    
    @Column(name = "maximum_quantity", precision = 10, scale = 2)
    private BigDecimal maximumQuantity;
    
    @Column(name = "reorder_point", precision = 10, scale = 2)
    private BigDecimal reorderPoint;
    
    @Column(name = "location_details")
    private String locationDetails;
    
    public enum StockStatus {
        NORMAL, LOW, CRITICAL, OUT_OF_STOCK, OVERSTOCKED
    }
    
    public void updateStatus() {
        if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
            this.status = StockStatus.OUT_OF_STOCK;
        } else if (maximumQuantity != null && quantity.compareTo(maximumQuantity) > 0) {
            this.status = StockStatus.OVERSTOCKED;
        } else if (reorderPoint != null && quantity.compareTo(reorderPoint) <= 0) {
            this.status = StockStatus.LOW;
        } else if (minimumQuantity != null && quantity.compareTo(minimumQuantity) <= 0) {
            this.status = StockStatus.CRITICAL;
        } else {
            this.status = StockStatus.NORMAL;
        }
    }
    
    public void addStock(BigDecimal amount) {
        this.quantity = this.quantity.add(amount);
        this.lastRestocked = LocalDateTime.now();
        updateStatus();
    }
    
    public void removeStock(BigDecimal amount) {
        if (this.quantity.compareTo(amount) < 0) {
            throw new IllegalStateException("Insufficient stock in shop");
        }
        this.quantity = this.quantity.subtract(amount);
        updateStatus();
    }
}
