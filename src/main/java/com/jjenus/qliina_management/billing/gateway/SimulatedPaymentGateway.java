package com.jjenus.qliina_management.billing.gateway;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Local, deterministic gateway for development and tests. Real providers plug
 * in behind {@link PaymentGateway} later.
 *
 * <p>Failure injection:
 * <ul>
 *   <li>{@code app.billing.simulator.failure-mode=ALL} — every charge fails;</li>
 *   <li>{@code forceFailure(true)} — programmatic (used by integration tests to
 *       drive the dunning/past_due path deterministically).</li>
 * </ul>
 */
@Slf4j
@Component
public class SimulatedPaymentGateway implements PaymentGateway {

    public static final String GATEWAY_NAME = "simulator";
    private static final BigDecimal SIMULATED_FEE_PERCENT = new BigDecimal("0.015");

    private final boolean failAll;
    private final String secret;

    @Getter
    private volatile boolean forceFailure = false;

    public SimulatedPaymentGateway(
            @Value("${app.billing.simulator.failure-mode:NONE}") String failureMode,
            @Value("${app.billing.gateway.simulator.secret:sim-secret}") String secret) {
        this.failAll = "ALL".equalsIgnoreCase(failureMode);
        this.secret = secret;
    }

    @Override
    public String getName() {
        return GATEWAY_NAME;
    }

    @Override
    public ChargeResult charge(ChargeRequest request) {
        boolean failed = failAll || forceFailure;
        String txnId = "sim_" + UUID.randomUUID();
        if (failed) {
            String raw = jsonResponse(txnId, "failed", request.amount(), "declined");
            log.info("[sim] charge DECLINED amount={} idem={}", request.amount(), request.idempotencyKey());
            return new ChargeResult(false, txnId, BigDecimal.ZERO, raw, "Simulated decline");
        }
        BigDecimal fee = request.amount().multiply(SIMULATED_FEE_PERCENT)
                .setScale(2, RoundingMode.HALF_UP);
        String raw = jsonResponse(txnId, "succeeded", request.amount(), "approved");
        log.info("[sim] charge APPROVED amount={} fee={} idem={}", request.amount(), fee, request.idempotencyKey());
        return new ChargeResult(true, txnId, fee, raw, "Simulated approval");
    }

    @Override
    public ChargeResult refund(RefundRequest request) {
        String txnId = "sim_ref_" + UUID.randomUUID();
        return new ChargeResult(true, txnId, BigDecimal.ZERO,
                jsonResponse(txnId, "succeeded", request.amount(), "refunded"),
                "Simulated refund");
    }

    @Override
    public GatewayWebhookEvent parseWebhook(String rawPayload, Map<String, String> headers) {
        // Simulator webhooks are the raw JSON we produce: {"txn":"…","event":"charge.succeeded",…}
        boolean paid = rawPayload.contains("charge.succeeded");
        String eventType = paid ? "charge.succeeded" : "charge.failed";
        String txn = extract(rawPayload, "\"txn\":\"");
        BigDecimal amount = extractAmount(rawPayload);
        return new GatewayWebhookEvent(eventType, txn, amount, paid, rawPayload);
    }

    @Override
    public boolean verifySignature(String rawPayload, Map<String, String> headers) {
        String provided = headers.get("x-sim-secret");
        return secret != null && secret.equals(provided);
    }

    /** Test hook — force the next charges to fail so dunning can be exercised. */
    public void forceFailure(boolean force) {
        this.forceFailure = force;
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
