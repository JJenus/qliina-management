package com.jjenus.qliina_management.billing.service;

import com.jjenus.qliina_management.billing.model.BillingInvoice;
import com.jjenus.qliina_management.billing.model.InvoiceLineItem;
import com.jjenus.qliina_management.billing.model.InvoiceStatus;
import com.jjenus.qliina_management.billing.model.LineItemType;
import com.jjenus.qliina_management.billing.repository.BillingInvoiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Invoice construction and lifecycle (MD §2 invoices, §7). Invoices are
 * containers of priced line items — append-only, never mutated after issue.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceService {

    private final BillingInvoiceRepository invoiceRepository;
    private final InvoiceNumberService numberService;

    public record LineItem(LineItemType type, String description, BigDecimal amount,
                           LocalDateTime periodStart, LocalDateTime periodEnd) {
    }

    /**
     * Creates an OPEN invoice with the given line items. Idempotent on
     * {@code idempotencyKey}: a redelivered request returns the existing invoice
     * instead of creating a duplicate (§8). Caller must be in a transaction.
     */
    @Transactional
    public BillingInvoice createInvoice(UUID subscriptionId, String idempotencyKey,
                                        LocalDateTime issuedAt, LocalDateTime dueAt,
                                        List<LineItem> items) {
        if (idempotencyKey != null) {
            BillingInvoice existing = invoiceRepository.findByIdempotencyKey(idempotencyKey).orElse(null);
            if (existing != null) {
                log.info("Invoice idempotency hit: {} (invoice {})", idempotencyKey, existing.getInvoiceNumber());
                return existing;
            }
        }
        LocalDateTime now = issuedAt != null ? issuedAt : LocalDateTime.now();
        BillingInvoice invoice = BillingInvoice.builder()
                .subscriptionId(subscriptionId)
                .invoiceNumber(numberService.nextInvoiceNumber(now))
                .idempotencyKey(idempotencyKey)
                .status(InvoiceStatus.OPEN)
                .issuedAt(now)
                .dueAt(dueAt)
                .amount(BigDecimal.ZERO)
                .build();
        for (LineItem item : items) {
            invoice.addLineItem(InvoiceLineItem.builder()
                    .type(item.type())
                    .description(item.description())
                    .amount(item.amount())
                    .periodStart(item.periodStart())
                    .periodEnd(item.periodEnd())
                    .build());
        }
        invoice.recomputeAmount();
        BillingInvoice saved = invoiceRepository.save(invoice);
        log.info("Invoice {} created for subscription {} amount={}",
                saved.getInvoiceNumber(), subscriptionId, saved.getAmount());
        return saved;
    }

    @Transactional
    public void markPaid(BillingInvoice invoice) {
        if (invoice.getStatus() == InvoiceStatus.PAID) return;
        invoice.setStatus(InvoiceStatus.PAID);
        invoiceRepository.save(invoice);
    }

    @Transactional
    public void markFailed(BillingInvoice invoice) {
        if (invoice.getStatus() == InvoiceStatus.FAILED) return;
        invoice.setStatus(InvoiceStatus.FAILED);
        invoiceRepository.save(invoice);
    }
}
