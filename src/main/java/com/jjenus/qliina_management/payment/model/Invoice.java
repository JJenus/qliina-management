package com.jjenus.qliina_management.payment.model;

import com.jjenus.qliina_management.common.BaseTenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "invoices")
@Getter
@Setter
public class Invoice extends BaseTenantEntity {
    
    @Column(name = "invoice_number", nullable = false, unique = true)
    private String invoiceNumber;
    
    @Column(name = "account_id", nullable = false)
    private java.util.UUID accountId;
    
    @Column(name = "company_name")
    private String companyName;
    
    @Column(name = "period_start")
    private LocalDate periodStart;
    
    @Column(name = "period_end")
    private LocalDate periodEnd;
    
    @Column(name = "due_date")
    private LocalDate dueDate;
    
    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<InvoiceItem> items = new ArrayList<>();
    
    @Column(name = "subtotal", precision = 10, scale = 2)
    private BigDecimal subtotal;
    
    @Column(name = "tax", precision = 10, scale = 2)
    private BigDecimal tax;
    
    @Column(name = "total", precision = 10, scale = 2)
    private BigDecimal total;
    
    @Column(name = "status")
    private String status;
    
    @Column(name = "pdf_url")
    private String pdfUrl;
    
    @Column(name = "sent_at")
    private LocalDateTime sentAt;
    
    @Column(name = "paid_at")
    private LocalDateTime paidAt;
}