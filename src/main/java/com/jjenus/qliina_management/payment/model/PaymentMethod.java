package com.jjenus.qliina_management.payment.model;

import com.jjenus.qliina_management.common.BaseTenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "payment_methods")
@Getter
@Setter
public class PaymentMethod extends BaseTenantEntity {
    
    @Column(name = "name", nullable = false)
    private String name;
    
    @Column(name = "type", nullable = false)
    private String type;
    
    @Column(name = "icon")
    private String icon;
    
    @Column(name = "is_active")
    private Boolean isActive = true;
    
    @Column(name = "requires_reference")
    private Boolean requiresReference = false;
    
    @Column(name = "surcharge", precision = 5, scale = 2)
    private BigDecimal surcharge;
    
    @Column(name = "min_amount", precision = 10, scale = 2)
    private BigDecimal minAmount;
    
    @Column(name = "max_amount", precision = 10, scale = 2)
    private BigDecimal maxAmount;
}