package com.jjenus.qliina_management.inventory.model;

import com.jjenus.qliina_management.common.BaseTenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "inventory_items", indexes = {
    @Index(name = "idx_inventory_sku", columnList = "sku", unique = true),
    @Index(name = "idx_inventory_category", columnList = "category"),
    @Index(name = "idx_inventory_supplier", columnList = "supplier_id")
})
@Getter
@Setter
public class InventoryItem extends BaseTenantEntity {
    
    @Column(name = "sku", nullable = false, unique = true)
    private String sku;
    
    @Column(name = "name", nullable = false)
    private String name;
    
    @Column(name = "description")
    private String description;
    
    @Column(name = "category", nullable = false)
    @Enumerated(EnumType.STRING)
    private ItemCategory category;
    
    @Column(name = "unit", nullable = false)
    @Enumerated(EnumType.STRING)
    private UnitOfMeasure unit;
    
    @Column(name = "reorder_level", nullable = false)
    private Integer reorderLevel;
    
    @Column(name = "reorder_quantity", nullable = false)
    private Integer reorderQuantity;
    
    @Column(name = "current_stock", precision = 10, scale = 2)
    private BigDecimal currentStock = BigDecimal.ZERO;
    
    @Column(name = "unit_price", precision = 10, scale = 2)
    private BigDecimal unitPrice;
    
    @Column(name = "supplier_id")
    private java.util.UUID supplierId;
    
    @Column(name = "is_active")
    private Boolean isActive = true;
    
    @Column(name = "min_stock_level", precision = 10, scale = 2)
    private BigDecimal minStockLevel;
    
    @Column(name = "max_stock_level", precision = 10, scale = 2)
    private BigDecimal maxStockLevel;
    
    @Column(name = "location")
    private String location;
    
    @OneToMany(mappedBy = "item", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<ShopStock> shopStocks = new HashSet<>();
    
    @OneToMany(mappedBy = "item", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<StockTransaction> transactions = new HashSet<>();
    
    @OneToMany(mappedBy = "item", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<StockAlert> alerts = new HashSet<>();
    
    public enum ItemCategory {
        DETERGENT, SOFTENER, BLEACH, STAIN_REMOVAL, PACKAGING, HANGER, 
        TAG, LABEL, GLOVE, MASK, CLEANING_SUPPLY, OTHER
    }
    
    public enum UnitOfMeasure {
        LITER, MILLILITER, KILOGRAM, GRAM, PIECE, BOX, CASE, BOTTLE, BAG, ROLL
    }
    
    public void addStock(BigDecimal quantity) {
        this.currentStock = this.currentStock.add(quantity);
    }
    
    public void removeStock(BigDecimal quantity) {
        if (this.currentStock.compareTo(quantity) < 0) {
            throw new IllegalStateException("Insufficient stock. Available: " + this.currentStock + ", Requested: " + quantity);
        }
        this.currentStock = this.currentStock.subtract(quantity);
    }
    
    public boolean isLowStock() {
        return this.currentStock.compareTo(BigDecimal.valueOf(this.reorderLevel)) <= 0;
    }
    
    public boolean isCriticalStock() {
        return this.currentStock.compareTo(BigDecimal.valueOf(this.reorderLevel / 2)) <= 0;
    }
}
