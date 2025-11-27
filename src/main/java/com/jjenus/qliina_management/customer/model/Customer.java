package com.jjenus.qliina_management.customer.model;

import com.jjenus.qliina_management.common.BaseTenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "customers", indexes = {
    @Index(name = "idx_customer_phone", columnList = "phone"),
    @Index(name = "idx_customer_email", columnList = "email"),
    @Index(name = "idx_customer_business", columnList = "business_id"),
    @Index(name = "idx_customer_loyalty_tier", columnList = "loyalty_tier"),
    @Index(name = "idx_customer_rfm_segment", columnList = "rfm_segment")
})
@Getter
@Setter
public class Customer extends BaseTenantEntity {
    
    @Column(name = "first_name", nullable = false)
    private String firstName;
    
    @Column(name = "last_name", nullable = false)
    private String lastName;
    
    @Column(name = "phone", nullable = false)
    private String phone;
    
    @Column(name = "email")
    private String email;
    
    @Column(name = "total_orders")
    private Integer totalOrders = 0;
    
    @Column(name = "total_spent", precision = 10, scale = 2)
    private BigDecimal totalSpent = BigDecimal.ZERO;
    
    @Column(name = "average_order_value", precision = 10, scale = 2)
    private BigDecimal averageOrderValue = BigDecimal.ZERO;
    
    @Column(name = "last_order_date")
    private LocalDateTime lastOrderDate;
    
    @Column(name = "loyalty_points")
    private Integer loyaltyPoints = 0;
    
    @Column(name = "loyalty_tier")
    private String loyaltyTier = "BRONZE";
    
    @Column(name = "rfm_segment")
    private String rfmSegment;
    
    @Column(name = "tags", columnDefinition = "text[]")
    private List<String> tags = new ArrayList<>();
    
    @Column(name = "enabled")
    private Boolean enabled = true;
    
    @OneToMany(mappedBy = "customer", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("createdAt DESC")
    private List<CustomerAddress> addresses = new ArrayList<>();
    
    @OneToOne(mappedBy = "customer", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private CustomerPreferences preferences;
    
    @OneToMany(mappedBy = "customer", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("createdAt DESC")
    private List<CustomerNote> notes = new ArrayList<>();
    
    @OneToMany(mappedBy = "customer", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("createdAt DESC")
    private List<LoyaltyTransaction> loyaltyTransactions = new ArrayList<>();
    
    public void addOrder(BigDecimal amount) {
        this.totalOrders++;
        this.totalSpent = this.totalSpent.add(amount);
        this.averageOrderValue = this.totalSpent.divide(BigDecimal.valueOf(this.totalOrders), 2, java.math.RoundingMode.HALF_UP);
        this.lastOrderDate = LocalDateTime.now();
    }
    
    public void addLoyaltyPoints(int points) {
        this.loyaltyPoints += points;
    }
    
    public void deductLoyaltyPoints(int points) {
        if (this.loyaltyPoints < points) {
            throw new IllegalStateException("Insufficient loyalty points");
        }
        this.loyaltyPoints -= points;
    }
}
