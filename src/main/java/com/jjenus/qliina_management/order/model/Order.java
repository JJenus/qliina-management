package com.jjenus.qliina_management.order.model;

import com.jjenus.qliina_management.common.BaseTenantEntity;
import com.jjenus.qliina_management.payment.model.OrderPayment;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Entity
@Table(name = "orders", indexes = {
    @Index(name = "idx_order_number", columnList = "orderNumber", unique = true),
    @Index(name = "idx_order_tracking", columnList = "trackingNumber", unique = true),
    @Index(name = "idx_order_customer", columnList = "customerId"),
    @Index(name = "idx_order_status", columnList = "status"),
    @Index(name = "idx_order_dates", columnList = "receivedAt")
})
@Getter
@Setter
public class Order extends BaseTenantEntity {
    
    @Column(name = "order_number", nullable = false, unique = true)
    private String orderNumber;
    
    @Column(name = "tracking_number", nullable = false, unique = true)
    private String trackingNumber;
    
    @Column(name = "customer_id", nullable = false)
    private UUID customerId;
    
    @Column(name = "invoice_id")
    private UUID invoiceId;
    
    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private OrderStatus status;
    
    @Column(name = "priority")
    @Enumerated(EnumType.STRING)
    private Priority priority = Priority.NORMAL;
    
    @Column(name = "item_count")
    private Integer itemCount;
    
    @Column(name = "total_amount", precision = 10, scale = 2)
    private BigDecimal totalAmount;
    
    @Column(name = "paid_amount", precision = 10, scale = 2)
    private BigDecimal paidAmount = BigDecimal.ZERO;
    
    @Column(name = "balance_due", precision = 10, scale = 2)
    private BigDecimal balanceDue;
    
    @Column(name = "received_at")
    private LocalDateTime receivedAt;
    
    @Column(name = "expected_ready_at")
    private LocalDateTime expectedReadyAt;
    
    @Column(name = "actual_ready_at")
    private LocalDateTime actualReadyAt;
    
    @Column(name = "completed_at")
    private LocalDateTime completedAt;
    
    @Column(name = "promised_date")
    private LocalDateTime promisedDate;
    
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("createdAt ASC")
    private List<OrderItem> items = new ArrayList<>();
    
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("createdAt DESC")
    private List<OrderNote> notes = new ArrayList<>();
    
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("timestamp ASC")
    private List<OrderTimeline> timeline = new ArrayList<>();
    
    @ElementCollection
    @CollectionTable(name = "order_tags", joinColumns = @JoinColumn(name = "order_id"))
    @Column(name = "tag")
    private Set<String> tags = new HashSet<>();
    
    @Embedded
    private DeliveryInfo deliveryInfo;
    
    public void markReady() {
        if (status != OrderStatus.QUALITY_CHECK) {
            throw new IllegalStateException("Order must pass quality check first");
        }
        this.status = OrderStatus.READY_FOR_PICKUP;
        this.actualReadyAt = LocalDateTime.now();
    }
    
    public void markCompleted() {
        if (status != OrderStatus.READY_FOR_PICKUP && status != OrderStatus.OUT_FOR_DELIVERY) {
            throw new IllegalStateException("Order must be ready for pickup or out for delivery");
        }
        this.status = OrderStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
    }
    
    public void addPayment(BigDecimal amount) {
        this.paidAmount = this.paidAmount.add(amount);
        this.balanceDue = this.totalAmount.subtract(this.paidAmount);
    }
    
    public enum OrderStatus {
        DRAFT, RECEIVED, WASHING, WASHED, IRONING, IRONED, 
        QUALITY_CHECK, READY_FOR_PICKUP, OUT_FOR_DELIVERY, 
        COMPLETED, ARCHIVED
    }
    
    public enum Priority {
        NORMAL, EXPRESS, URGENT
    }
}
