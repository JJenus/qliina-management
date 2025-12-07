package com.jjenus.qliina_management.inventory.model;

import com.jjenus.qliina_management.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "purchase_order_items")
@Getter
@Setter
public class PurchaseOrderItem extends BaseEntity {
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "po_id", nullable = false)
    private PurchaseOrder purchaseOrder;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private InventoryItem item;
    
    @Column(name = "quantity", nullable = false)
    private Integer quantity;
    
    @Column(name = "received_quantity")
    private Integer receivedQuantity;
    
    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;
    
    @Column(name = "total", precision = 10, scale = 2)
    private BigDecimal total;
    
    @Column(name = "notes")
    private String notes;
    
    public void calculateTotal() {
        this.total = this.unitPrice.multiply(BigDecimal.valueOf(this.quantity));
    }
    
    public boolean isFullyReceived() {
        return receivedQuantity != null && receivedQuantity >= quantity;
    }
}
