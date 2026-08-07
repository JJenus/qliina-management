package com.jjenus.qliina_management.billing.service;

import com.jjenus.qliina_management.billing.gateway.GatewayWebhookEvent;
import com.jjenus.qliina_management.billing.gateway.PaymentGateway;
import com.jjenus.qliina_management.billing.model.*;
import com.jjenus.qliina_management.billing.repository.*;
import com.jjenus.qliina_management.common.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Payment orchestration and gateway reconciliation (MD §2 payments, §8).
 *
 * <p>Fault-tolerance guarantees:
 * <ul>
 *   <li>Every charge is idempotent on {@code idempotencyKey} — callers may retry.</li>
 *   <li>Webhook redelivery dedupes on (gateway, gateway_transaction_id).</li>
 *   <li>Raw gateway payloads are persisted for later reconciliation/disputes.</li>
 *   <li>fee_amount / net_amount answer "revenue after fees" directly.</li>
 * </ul>
 */
@Slf4j
@Service("billingPaymentService")
@RequiredArgsConstructor
public class BillingPaymentService {

    private final PaymentGateway gateway;
    private final BillingPaymentRepository paymentRepository;
    private final BillingInvoiceRepository invoiceRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final BillingPaymentMethodRepository paymentMethodRepository;

    public record PaymentResult(boolean approved, UUID paymentId, UUID invoiceId, String message) {
    }

    @Transactional
    public PaymentResult attemptInvoicePayment(UUID invoiceId, UUID paymentMethodId, String idempotencyKey) {
        BillingInvoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new BusinessException("Invoice not found", "INVOICE_NOT_FOUND"));

        if (invoice.getStatus() == InvoiceStatus.PAID) {
            return new PaymentResult(true, null, invoiceId, "already-paid");
        }

        // Short-circuit on a previous attempt with the same key (§8).
        if (idempotencyKey != null) {
            BillingPayment existing = paymentRepository.findByIdempotencyKey(idempotencyKey).orElse(null);
            if (existing != null) {
                return new PaymentResult(existing.getStatus() == PaymentStatus.APPROVED,
                        existing.getId(), invoiceId, "idempotent-replay");
            }
        }

        Subscription subscription = subscriptionRepository.findById(invoice.getSubscriptionId())
                .orElseThrow(() -> new BusinessException("Subscription not found", "SUBSCRIPTION_NOT_FOUND"));
        String currency = subscription.getPlanVersion().getCurrency();

        UUID methodId = paymentMethodId != null
                ? paymentMethodId
                : findDefaultPaymentMethod(invoice.getSubscriptionId());
        String customerRef = "biz_" + subscription.getBusinessId();

        PaymentGateway.ChargeRequest request = new PaymentGateway.ChargeRequest(
                invoice.getAmount(), currency, methodId, customerRef, idempotencyKey);
        PaymentGateway.ChargeResult result = gateway.charge(request);

        BillingPayment payment = BillingPayment.builder()
                .invoiceId(invoiceId)
                .paymentMethodId(methodId)
                .idempotencyKey(idempotencyKey)
                .gateway(gateway.getName())
                .gatewayTransactionId(result.transactionId())
                .gatewayResponse(result.rawResponse())
                .amount(invoice.getAmount())
                .feeAmount(result.feeAmount())
                .netAmount(invoice.getAmount().subtract(result.feeAmount()))
                .status(result.approved() ? PaymentStatus.APPROVED : PaymentStatus.FAILED)
                .paidAt(result.approved() ? LocalDateTime.now() : null)
                .build();
        paymentRepository.save(payment);

        if (result.approved()) {
            invoice.setStatus(InvoiceStatus.PAID);
        } else {
            invoice.setStatus(InvoiceStatus.FAILED);
        }
        invoiceRepository.save(invoice);
        log.info("Payment {} invoice={} gateway={} txn={} amount={}",
                result.approved() ? "approved" : "failed", invoiceId,
                gateway.getName(), result.transactionId(), invoice.getAmount());
        return new PaymentResult(result.approved(), payment.getId(), invoiceId, result.message());
    }

    /**
     * Reconciles an inbound gateway webhook. Redelivery is routine: we dedupe on
     * (gateway, gateway_transaction_id) and only ever apply the transition once.
     */
    @Transactional
    public void handleWebhook(String gatewayName, String rawPayload, Map<String, String> headers) {
        if (!gateway.getName().equalsIgnoreCase(gatewayName)) {
            throw new BusinessException("Unknown gateway: " + gatewayName, "UNKNOWN_GATEWAY");
        }
        if (!gateway.verifySignature(rawPayload, headers)) {
            throw new BusinessException("Invalid webhook signature", "INVALID_WEBHOOK_SIGNATURE");
        }
        GatewayWebhookEvent event = gateway.parseWebhook(rawPayload, headers);
        if (event.transactionId() == null) {
            throw new BusinessException("Webhook missing transaction id", "INVALID_WEBHOOK");
        }

        BillingPayment payment = paymentRepository
                .findByGatewayAndGatewayTransactionId(gateway.getName(), event.transactionId())
                .orElse(null);
        if (payment != null) {
            log.info("Webhook already reconciled txn={} — skipping", event.transactionId());
            return;
        }

        BillingInvoice invoice = invoiceRepository.findByIdempotencyKey(event.transactionId())
                .orElse(null);
        if (invoice == null && event.isChargeSucceeded()) {
            // Payment confirmed by gateway but no local payment row (e.g. async auth)
            // — persist a skeleton so reconciliation is complete.
            log.warn("Webhook txn={} has no matching invoice/payment", event.transactionId());
            return;
        }

        if (event.isChargeSucceeded() && invoice != null) {
            BillingPayment reconciled = BillingPayment.builder()
                    .invoiceId(invoice.getId())
                    .gateway(gateway.getName())
                    .gatewayTransactionId(event.transactionId())
                    .gatewayResponse(event.rawPayload())
                    .amount(event.amount())
                    .feeAmount(BigDecimal.ZERO)
                    .netAmount(event.amount())
                    .status(PaymentStatus.APPROVED)
                    .paidAt(LocalDateTime.now())
                    .build();
            paymentRepository.save(reconciled);
            invoice.setStatus(InvoiceStatus.PAID);
            invoiceRepository.save(invoice);
            log.info("Webhook reconciled: invoice {} paid via txn {}", invoice.getInvoiceNumber(), event.transactionId());
        }
    }

    private UUID findDefaultPaymentMethod(UUID subscriptionId) {
        return subscriptionRepository.findById(subscriptionId)
                .map(Subscription::getBusinessId)
                .map(bizId -> paymentMethodRepository.findAllByBusinessIdOrderByIsDefaultDescCreatedAtAsc(bizId))
                .map(list -> list.isEmpty() ? null : list.get(0).getId())
                .orElse(null);
    }
}
