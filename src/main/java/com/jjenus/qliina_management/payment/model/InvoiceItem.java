// src/main/java/com/jjenus/qliina_management/payment/model/InvoiceItem.java
package com.jjenus.qliina_management.payment.model;

import com.jjenus.qliina_management.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "invoice_items")
@Getter
@Setter
public class InvoiceItem extends BaseEntity {
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;
    
    @Column(name = "order_id")
    private UUID orderId;
    
    @Column(name = "order_number")
    private String orderNumber;
    
    @Column(name = "order_date")
    private LocalDateTime orderDate;
    
    @Column(name = "amount", precision = 10, scale = 2)
    private BigDecimal amount;
    
    @Column(name = "status")
    private String status;
}