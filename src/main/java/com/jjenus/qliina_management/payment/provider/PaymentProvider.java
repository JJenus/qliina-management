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

    /**
     * True when charges can be routed through a Qliina-owned subaccount
     * ({@code PLATFORM} mode). Real processors typically support it; providers
     * that do not reject a {@code platformSubaccountId} at configuration time.
     */
    default boolean supportsPlatformSubaccounts() {
        return false;
    }

    ChargeResult charge(ChargeRequest request);

    /**
     * Initiates a hosted-checkout payment for a customer-facing link/QR. The
     * result is always redirect-style ({@code approved=false} + {@code checkoutUrl})
     * so the customer completes the payment on the processor's own page. Defaults
     * to {@link #charge} — the real processors' {@code charge()} is already an
     * initialize/authorize call — while the simulator overrides it to produce a
     * deterministic checkout link.
     */
    default ChargeResult initiateCheckout(ChargeRequest request) {
        return charge(request);
    }

    VerifyResult verify(String providerReference);

    /**
     * Verifies a charge using the business's own connection context. Defaults to
     * {@link #verify(String)} for providers whose platform secret is always the
     * correct credential; BYO-connected businesses override this so rechecking
     * authenticates with the merchant's own key.
     */
    default VerifyResult verify(String providerReference, Connection connection) {
        return verify(providerReference);
    }

    RefundResult refund(RefundRequest request);

    /**
     * Reverses a charge using the business's own connection context. Defaults to
     * {@link #refund(RefundRequest)}; BYO-connected businesses override this so
     * the reversal authenticates with the merchant's own key.
     */
    default RefundResult refund(RefundRequest request, Connection connection) {
        return refund(request);
    }

    /** Parses a raw webhook payload into a canonical event for reconciliation. */
    WebhookEvent parseWebhook(String rawPayload, Map<String, String> headers);

    /** Verifies the webhook signature (HMAC/secret header) for this provider. */
    boolean verifyWebhookSignature(String rawPayload, Map<String, String> headers);

    record ChargeRequest(BigDecimal amount, String currency, String customerEmail,
                         String customerName, String reference, String description, Connection connection) {
    }

    /**
     * Per-business context resolved at charge time: how this business connects
     * ({@code PLATFORM} subaccount vs {@code BYO} business credentials) and the
     * effective secret to authenticate with. {@code credentials()} is the already
     * decrypted BYO secret key; it is null for platform-connected charges.
     */
    record Connection(String mode, String platformSubaccountId, String credentials) {
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
                        boolean chargeSucceeded, boolean chargeFailed, String rawPayload) {
    }
}