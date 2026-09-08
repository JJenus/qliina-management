package com.jjenus.qliina_management.payment.provider;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Gateway-agnostic payment-provider boundary for the POS checkout. A real
 * processor (Paystack/Flutterwave/…) plugs in behind this interface without
 * touching the checkout engine.
 *
 * <p>Lifecycle (*configure → charge → verify → refund*):
 * <ol>
 *   <li>{@code isConfigured()} — secrets are resolvable; a provider that is not
 *       configured must never be shown at checkout nor charged (fail closed).</li>
 *   <li>{@code charge()} — initiates the payment. Redirect-based processors
 *       return an authorization URL and {@code approved=false} (PENDING) — the
 *       merchant then redirects the customer and confirms settlement via
 *       {@link #verify} or a webhook.</li>
 *   <li>{@code verify()} — polls the processor for the final status of a charge.</li>
 *   <li>{@code refund()} — reverses a previously settled charge.</li>
 * </ol>
 */
public interface PaymentProvider {

    /** Stable machine name, e.g. {@code simulator}, {@code paystack}. */
    String getName();

    /** Human-friendly name shown to merchants, e.g. "Paystack". */
    String getDisplayName();

    /** POS payment methods this provider can process (e.g. CARD, TRANSFER). */
    List<String> supportedMethods();

    /** True when this provider has the secrets it needs to talk to the processor. */
    boolean isConfigured();

    ChargeResult charge(ChargeRequest request);

    VerifyResult verify(String providerReference);

    RefundResult refund(RefundRequest request);

    /** Parses a raw webhook payload into a canonical event for reconciliation. */
    WebhookEvent parseWebhook(String rawPayload, Map<String, String> headers);

    /** Verifies the webhook signature (HMAC/secret header) for this provider. */
    boolean verifyWebhookSignature(String rawPayload, Map<String, String> headers);

    record ChargeRequest(BigDecimal amount, String currency, String customerEmail,
                         String customerName, String reference, String description) {
    }

    record ChargeResult(boolean approved, String providerReference, String checkoutUrl,
                        BigDecimal feeAmount, String status, String rawResponse, String message) {
    }

    record VerifyResult(boolean paid, String providerReference, String status, String rawResponse) {
    }

    record RefundRequest(String providerReference, BigDecimal amount, String reason, String idempotencyKey) {
    }

    record RefundResult(boolean approved, String providerReference, String rawResponse, String message) {
    }

    record WebhookEvent(BigDecimal amount, String providerReference,
                        boolean chargeSucceeded, String rawPayload) {
    }
}