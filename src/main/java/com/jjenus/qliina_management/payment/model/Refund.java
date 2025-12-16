package com.jjenus.qliina_management.payment.model;

import com.jjenus.qliina_management.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "refunds")
@Getter
@Setter
public class Refund extends BaseEntity {
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", nullable = false)
    private OrderPayment payment;
    
    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;
    
    @Column(name = "reason", nullable = false)
    private String reason;
    
    @Column(name = "reason_code")
    private String reasonCode;
    
    @Column(name = "method")
    private String method;
    
    @Column(name = "status")
    private String status;
    
    @Column(name = "processed_at")
    private LocalDateTime processedAt;
    
    @Column(name = "processed_by")
    private UUID processedBy;
    
    @Column(name = "notes")
    private String notes;
    
    @Column(name = "approval_code")
    private String approvalCode;
    
    @Column(name = "business_id", nullable = false)
    private UUID businessId;
}