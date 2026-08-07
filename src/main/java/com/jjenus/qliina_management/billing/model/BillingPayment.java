package com.jjenus.qliina_management.billing.model;

import com.jjenus.qliina_management.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A payment settled against an invoice (MD §2 payments, §8). References a real
 * gateway transaction; the raw webhook payload is kept for dispute/debugging.
 */
@Entity
@Table(name = "billing_payments", uniqueConstraints = {
    @UniqueConstraint(name = "uq_billing_payment_txn", columnNames = {"gateway", "gateway_transaction_id"})
}, indexes = {
    @Index(name = "idx_billing_payments_invoice", columnList = "invoice_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillingPayment extends BaseEntity {

    @Column(name = "invoice_id", nullable = false)
    private UUID invoiceId;

    @Column(name = "payment_method_id")
    private UUID paymentMethodId;

    /** Idempotency guard — the caller may retry (§8). */
    @Column(name = "idempotency_key", unique = true)
    private String idempotencyKey;

    @Column(name = "gateway", nullable = false, length = 30)
    private String gateway;

    /** Join key back to the gateway's own statements (§8). */
    @Column(name = "gateway_transaction_id", nullable = false, length = 128)
    private String gatewayTransactionId;

    /** Raw webhook payload, kept for reconciliation (§8). */
    @Column(name = "gateway_response", columnDefinition = "TEXT")
    private String gatewayResponse;

    @Column(name = "amount", precision = 12, scale = 2, nullable = false)
    private BigDecimal amount;

    @Column(name = "fee_amount", precision = 12, scale = 2, nullable = false)
    private BigDecimal feeAmount = BigDecimal.ZERO;

    @Column(name = "net_amount", precision = 12, scale = 2, nullable = false)
    private BigDecimal netAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;
}
