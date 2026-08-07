package com.jjenus.qliina_management.billing.model;

import com.jjenus.qliina_management.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Billing invoice — a container for priced line items (MD §2 invoices, §7).
 * `amount` is a cached sum of line items; never a single mutable figure.
 */
@Entity
@Table(name = "billing_invoices", indexes = {
    @Index(name = "idx_billing_invoices_sub", columnList = "subscription_id"),
    @Index(name = "idx_billing_invoices_status", columnList = "status")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillingInvoice extends BaseEntity {

    @Column(name = "subscription_id", nullable = false)
    private UUID subscriptionId;

    /** Sequential, e.g. INV-2026-000123. Required for tax/accounting compliance. */
    @Column(name = "invoice_number", nullable = false, unique = true, length = 40)
    private String invoiceNumber;

    /** Idempotency guard against webhook/gateway redelivery (§8). */
    @Column(name = "idempotency_key", unique = true)
    private String idempotencyKey;

    @Column(name = "amount", precision = 12, scale = 2, nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private InvoiceStatus status;

    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt;

    @Column(name = "due_at")
    private LocalDateTime dueAt;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<InvoiceLineItem> lineItems = new ArrayList<>();

    public void addLineItem(InvoiceLineItem item) {
        item.setInvoice(this);
        lineItems.add(item);
    }

    public void recomputeAmount() {
        this.amount = lineItems.stream()
                .map(InvoiceLineItem::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
