package com.jjenus.qliina_management.payment.provider;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Local, deterministic provider for POS development and tests. Mirrors the
 * billing {@code SimulatedPaymentGateway} semantics:
 * <ul>
 *   <li>{@code app.payments.providers.simulator.enabled=false} — provider is not
 *       "configured" and refuses charges (fail closed). Default false in {@code prod}.</li>
 *   <li>{@code app.payments.providers.simulator.failure-mode=ALL} — every charge declines;</li>
 *   <li>{@code forceFailure(true)} — programmatic failure injection (integration tests).</li>
 * </ul>
 */
@Slf4j
@Component
public class SimulatorPaymentProvider implements PaymentProvider {

    public static final String PROVIDER_NAME = "simulator";

    private static final BigDecimal SIMULATED_FEE_PERCENT = new BigDecimal("0.015");

    private final boolean platformEnabled;
    private final boolean failAll;
    private final String secret;

    @Getter
    private volatile boolean forceFailure = false;

    /** Test hook — charges return a hosted checkout URL (redirect flow) instead of settling. */
    private volatile boolean redirectMode = false;

    public SimulatorPaymentProvider(
            @Value("${app.payments.providers.simulator.enabled:true}") boolean platformEnabled,
            @Value("${app.payments.providers.simulator.failure-mode:NONE}") String failureMode,
            @Value("${app.payments.providers.simulator.secret:sim-secret}") String secret) {
        this.platformEnabled = platformEnabled;
        this.failAll = "ALL".equalsIgnoreCase(failureMode);
        this.secret = secret;
    }

    @Override
    public String getName() {
        return PROVIDER_NAME;
    }

    @Override
    public String getDisplayName() {
        return "Simulator (sandbox)";
    }

    @Override
    public List<String> supportedMethods() {
        return List.of("CARD", "TRANSFER");
    }

    @Override
    public boolean isConfigured() {
        return platformEnabled;
    }

    @Override
    public ChargeResult charge(ChargeRequest request) {
        String txnId = "sim_" + UUID.randomUUID();
        if (redirectMode) {
            String checkoutUrl = "https://sandbox.simulator.local/checkout/" + txnId;
            String raw = jsonResponse(txnId, "pending", request.amount(), "pending");
            log.info("[sim] charge REDIRECT url={} ref={}", checkoutUrl, request.reference());
            return new ChargeResult(false, txnId, checkoutUrl, BigDecimal.ZERO, "PENDING", raw,
                    "Awaiting customer authorization");
        }
        if (!isConfigured() || failAll || forceFailure) {
            String raw = jsonResponse(txnId, "failed", request.amount(), "declined");
            log.info("[sim] charge DECLINED amount={} ref={}", request.amount(), request.reference());
            return new ChargeResult(false, txnId, null, BigDecimal.ZERO, "DECLINED", raw,
                    "Simulated decline");
        }
        BigDecimal fee = request.amount().multiply(SIMULATED_FEE_PERCENT)
                .setScale(2, RoundingMode.HALF_UP);
        String raw = jsonResponse(txnId, "succeeded", request.amount(), "approved");
        log.info("[sim] charge APPROVED amount={} fee={} ref={}", request.amount(), fee, request.reference());
        return new ChargeResult(true, txnId, null, fee, "SUCCESS", raw, "Simulated approval");
    }

    @Override
    public VerifyResult verify(String providerReference) {
        boolean paid = providerReference != null && !providerReference.startsWith("sim_declined");
        return new VerifyResult(paid, providerReference,
                paid ? "SUCCESS" : "FAILED", null);
    }

    @Override
    public RefundResult refund(RefundRequest request) {
        String txnId = "sim_ref_" + UUID.randomUUID();
        return new RefundResult(true, txnId,
                jsonResponse(txnId, "refunded", request.amount(), "refunded"), "Simulated refund");
    }

    @Override
    public WebhookEvent parseWebhook(String rawPayload, Map<String, String> headers) {
        boolean paid = rawPayload.contains("charge.succeeded");
        String txn = extract(rawPayload, "\"txn\":\"");
        BigDecimal amount = extractAmount(rawPayload);
        return new WebhookEvent(amount, txn, paid, rawPayload);
    }

    @Override
    public boolean verifyWebhookSignature(String rawPayload, Map<String, String> headers) {
        String provided = headers.get("x-sim-secret");
        return secret != null && secret.equals(provided);
    }

    /** Test hook — force the next charges to fail so decline paths can be exercised. */
    public void forceFailure(boolean force) {
        this.forceFailure = force;
    }

    /** Test hook — charges return a hostless checkout URL so the PENDING/redirect flow is testable. */
    public void forceRedirect(boolean redirect) {
        this.redirectMode = redirect;
    }

    private String jsonResponse(String txn, String event, BigDecimal amount, String status) {
        return String.format(
                "{\"txn\":\"%s\",\"event\":\"charge.%s\",\"amount\":%s,\"status\":\"%s\",\"ts\":\"%s\"}",
                txn, event, amount.toPlainString(), status, Instant.now().toString());
    }

    private String extract(String json, String key) {
        int start = json.indexOf(key);
        if (start < 0) return null;
        start += key.length();
        int end = json.indexOf('"', start);
        return end > start ? json.substring(start, end) : null;
    }

    private BigDecimal extractAmount(String json) {
        int start = json.indexOf("\"amount\":");
        if (start < 0) return BigDecimal.ZERO;
        start += "\"amount\":".length();
        int end = json.indexOf(',', start);
        if (end < 0) end = json.indexOf('}', start);
        try {
            return new BigDecimal(json.substring(start, end).trim());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }
}