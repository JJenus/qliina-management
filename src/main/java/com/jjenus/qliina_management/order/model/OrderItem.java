package com.jjenus.qliina_management.order.model;

import com.jjenus.qliina_management.common.BaseEntity;
import com.jjenus.qliina_management.quality.model.QualityCheck;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "order_items")
@Getter
@Setter
public class OrderItem extends BaseEntity {
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;
    
    @Column(name = "item_number", nullable = false)
    private String itemNumber;
    
    @Column(name = "barcode")
    private String barcode;
    
    @Column(name = "service_type", nullable = false)
    private String serviceType;

    /** Catalog id the service name was resolved from (null for legacy/quick orders). */
    @Column(name = "service_type_id")
    private UUID serviceTypeId;

    /**
     * Whether this garment must pass through washing before finishing.
     * Iron-only / dry-clean items go straight from RECEIVED to ironing.
     * Null-safe default: legacy rows and unknown services are treated as wash-required.
     */
    @Column(name = "requires_washing", nullable = false)
    private Boolean requiresWashing = true;
    
    @Column(name = "garment_type")
    private String garmentType;
    
    @Column(name = "description")
    private String description;
    
    @Column(name = "quantity", nullable = false)
    private Integer quantity;
    
    @Column(name = "weight", precision = 10, scale = 2)
    private BigDecimal weight;
    
    @Column(name = "unit_price", precision = 10, scale = 2)
    private BigDecimal unitPrice;
    
    @Column(name = "subtotal", precision = 10, scale = 2)
    private BigDecimal subtotal;
    
    @Column(name = "discount", precision = 10, scale = 2)
    private BigDecimal discount;
    
    @Column(name = "total", precision = 10, scale = 2)
    private BigDecimal total;
    
    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private ItemStatus status;
    
    @Column(name = "special_instructions")
    private String specialInstructions;
    
    @ElementCollection
    @CollectionTable(name = "item_images", joinColumns = @JoinColumn(name = "item_id"))
    @Column(name = "image_url")
    private List<String> images = new ArrayList<>();
    
    @OneToMany(mappedBy = "orderItem", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("timestamp DESC")
    private List<ItemStatusHistory> statusHistory = new ArrayList<>();

    /**
     * Individual unit records for multi-quantity items.
     * Empty for single-unit items (quantity == 1).
     */
    @OneToMany(mappedBy = "orderItem", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("unitNumber ASC")
    private List<OrderItemUnit> units = new ArrayList<>();
    
    public enum ItemStatus {
        PENDING, RECEIVED, WASHING, WASHED, IRONING, IRONED, 
        QUALITY_CHECK, COMPLETED, ISSUE_REPORTED
    }
}
