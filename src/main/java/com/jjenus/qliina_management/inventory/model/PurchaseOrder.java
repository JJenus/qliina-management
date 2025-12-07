package com.jjenus.qliina_management.inventory.model;

import com.jjenus.qliina_management.common.BaseTenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "purchase_orders", indexes = {
    @Index(name = "idx_po_number", columnList = "poNumber", unique = true),
    @Index(name = "idx_po_supplier", columnList = "supplier_id"),
    @Index(name = "idx_po_status", columnList = "status"),
    @Index(name = "idx_po_expected", columnList = "expected_delivery")
})
@Getter
@Setter
public class PurchaseOrder extends BaseTenantEntity {
    
    @Column(name = "po_number", nullable = false, unique = true)
    private String poNumber;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;
    
    @Column(name = "shop_id", nullable = false)
    private UUID shopId;
    
    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private PurchaseOrderStatus status;
    
    @Column(name = "order_date", nullable = false)
    private LocalDateTime orderDate;
    
    @Column(name = "expected_delivery")
    private LocalDate expectedDelivery;
    
    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;
    
    @Column(name = "delivered_by")
    private UUID deliveredBy;
    
    @OneToMany(mappedBy = "purchaseOrder", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<PurchaseOrderItem> items = new ArrayList<>();
    
    @Column(name = "subtotal", precision = 10, scale = 2)
    private BigDecimal subtotal;
    
    @Column(name = "tax", precision = 10, scale = 2)
    private BigDecimal tax;
    
    @Column(name = "shipping", precision = 10, scale = 2)
    private BigDecimal shipping;
    
    @Column(name = "total", precision = 10, scale = 2)
    private BigDecimal total;
    
    @Column(name = "notes")
    private String notes;
    
    @Column(name = "terms")
    private String terms;
    
    @Column(name = "approved_by")
    private UUID approvedBy;
    
    @Column(name = "approved_at")
    private LocalDateTime approvedAt;
    
    public enum PurchaseOrderStatus {
        DRAFT, SENT, CONFIRMED, SHIPPED, PARTIALLY_RECEIVED, RECEIVED, CANCELLED, REJECTED, APPROVED
    }
    
    public void calculateTotals() {
        this.subtotal = items.stream()
            .map(PurchaseOrderItem::getTotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        this.total = this.subtotal
            .add(this.tax != null ? this.tax : BigDecimal.ZERO)
            .add(this.shipping != null ? this.shipping : BigDecimal.ZERO);
    }
}
