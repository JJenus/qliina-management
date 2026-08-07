package com.jjenus.qliina_management.billing.gateway;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Gateway-agnostic payment boundary (§8). A real provider (Stripe/Paystack/…)
 * plugs in behind this interface without touching the billing engine.
 */
public interface PaymentGateway {

    String getName();

    ChargeResult charge(ChargeRequest request);

    ChargeResult refund(RefundRequest request);

    /** Parses a raw webhook payload into a canonical event for reconciliation. */
    GatewayWebhookEvent parseWebhook(String rawPayload, Map<String, String> headers);

    /** Verifies the webhook signature (HMAC/secret) for this gateway. */
    boolean verifySignature(String rawPayload, Map<String, String> headers);

    record ChargeRequest(BigDecimal amount, String currency, UUID paymentMethodId,
                         String customerReference, String idempotencyKey) {
    }

    record ChargeResult(boolean approved, String transactionId, BigDecimal feeAmount,
                        String rawResponse, String message) {
    }

    record RefundRequest(String transactionId, BigDecimal amount, String idempotencyKey) {
    }

    record RefundResult(boolean approved, String transactionId, String message) {
    }
}
