
package com.jjenus.qliina_management.payment.model;

import com.jjenus.qliina_management.common.BaseTenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "order_payments")
@Getter
@Setter
public class OrderPayment extends BaseTenantEntity {
    
    @Column(name = "order_id", nullable = false)
    private UUID orderId;
    
    @Column(name = "customer_id")
    private UUID customerId;
    
    @Column(name = "shop_id")
    private UUID shopId;
    
    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;
    
    @Column(name = "method", nullable = false)
    private String method;
    
    @Column(name = "reference")
    private String reference;
    
    @Column(name = "status")
    private String status;
    
    @Column(name = "paid_at", nullable = false)
    private LocalDateTime paidAt;
    
    @Column(name = "collected_by")
    private UUID collectedBy;
    
    @Column(name = "tip", precision = 10, scale = 2)
    private BigDecimal tip;
    
    @Column(name = "change_amount", precision = 10, scale = 2)
    private BigDecimal changeAmount;
    
    @Column(name = "metadata", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> metadata;
}