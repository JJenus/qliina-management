// src/main/java/com/jjenus/qliina_management/payment/model/CorporateAccount.java
package com.jjenus.qliina_management.payment.model;

import com.jjenus.qliina_management.common.BaseTenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "corporate_accounts")
@Getter
@Setter
public class CorporateAccount extends BaseTenantEntity {
    
    @Column(name = "customer_id", nullable = false, unique = true)
    private java.util.UUID customerId;
    
    @Column(name = "company_name", nullable = false)
    private String companyName;
    
    @Column(name = "tax_id")
    private String taxId;
    
    @Column(name = "credit_limit", nullable = false, precision = 10, scale = 2)
    private BigDecimal creditLimit;
    
    @Column(name = "current_balance", precision = 10, scale = 2)
    private BigDecimal currentBalance;
    
    @Column(name = "payment_terms")
    private String paymentTerms;
    
    @Column(name = "billing_cycle")
    private String billingCycle;
    
    @Embedded
    private BillingAddress billingAddress;
    
    @Column(name = "status")
    private String status;
    
    @Column(name = "last_invoice_date")
    private LocalDateTime lastInvoiceDate;
    
    @Column(name = "last_payment_date")
    private LocalDateTime lastPaymentDate;
    
    @Embeddable
    @Getter
    @Setter
    public static class BillingAddress {
        private String addressLine1;
        private String addressLine2;
        private String city;
        private String state;
        private String postalCode;
        private String country;
    }
}