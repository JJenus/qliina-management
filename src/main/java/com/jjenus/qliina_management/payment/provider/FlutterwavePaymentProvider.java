package com.jjenus.qliina_management.payment.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjenus.qliina_management.common.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Flutterwave integration for the POS checkout.
 *
 * <p>Redirect model: {@code charge()} calls {@code POST /v3/payments} (hosted
 * checkout page); the merchant redirects the customer; settlement is confirmed
 * via {@code GET /v3/transactions/{id}/verify} or the {@code charge.completed}
 * webhook. The webhook is signed with the {@code verif-hash} header (set in the
 * Flutterwave dashboard) and compared against the configured webhook secret.
 *
 * <p>The provider reference is the Flutterwave numeric transaction id, which
 * is stable across initialize/verify/refund/webhook. Fail-closed: no secret
 * means not configured and no live work is attempted.
 */
@Slf4j
@Component
public class FlutterwavePaymentProvider implements PaymentProvider {

    public static final String PROVIDER_NAME = "flutterwave";
    private static final String BASE_URL = "https://api.flutterwave.com/v3";
    private static final List<String> METHODS = List.of("CARD", "TRANSFER");

    private final RestClient client;
    private final ObjectMapper mapper;
    private final String secretKey;
    private final String webhookSecret;

    public FlutterwavePaymentProvider(
            @Value("${app.payments.providers.flutterwave.secret-key:}") String secretKey,
            @Value("${app.payments.providers.flutterwave.webhook-secret:}") String webhookSecret,
            ObjectMapper mapper) {
        this.secretKey = secretKey == null ? "" : secretKey.trim();
        this.webhookSecret = webhookSecret == null ? "" : webhookSecret.trim();
        this.mapper = mapper;
        this.client = RestClient.create(BASE_URL);
    }

    @Override
    public String getName() {
        return PROVIDER_NAME;
    }

    @Override
    public String getDisplayName() {
        return "Flutterwave";
    }

    @Override
    public List<String> supportedMethods() {
        return METHODS;
    }

    @Override
    public boolean isConfigured() {
        return !secretKey.isBlank();
    }

    @Override
    public ChargeResult charge(ChargeRequest request) {
        requireConfigured();
        Map<String, Object> body = Map.of(
                "tx_ref", request.reference(),
                "amount", request.amount().toPlainString(),
                "currency", request.currency(),
                "payment_options", "card,banktransfer",
                "customer", Map.of(
                        "email", request.customerEmail() == null ? "" : request.customerEmail(),
                        "name", request.customerName() == null ? "" : request.customerName()));
        String raw = rawPost("/payments", body);
        return parseInitialize(raw);
    }

    private ChargeResult parseInitialize(String raw) {
        try {
            JsonNode root = mapper.readTree(raw);
            String status = root.path("status").asText("");
            JsonNode data = root.path("data");
            String checkoutUrl = data.path("link").asText(null);
            String txnId = data.path("id").isNumber() ? String.valueOf(data.path("id").longValue()) : null;

            if ("success".equalsIgnoreCase(status) && checkoutUrl != null) {
                log.info("[flutterwave] initialize OK tx={}", txnId);
                return new ChargeResult(false, txnId, checkoutUrl, BigDecimal.ZERO,
                        "PENDING", raw, "Awaiting customer authorization");
            }
            String message = root.path("message").asText("Flutterwave initialize failed");
            log.warn("[flutterwave] initialize failed: {}", message);
            return new ChargeResult(false, txnId, null, BigDecimal.ZERO, "DECLINED", raw, message);
        } catch (Exception e) {
            log.error("[flutterwave] could not parse initialize response", e);
            return new ChargeResult(false, null, null, BigDecimal.ZERO, "DECLINED", raw,
                    "Unparseable Flutterwave response");
        }
    }

    @Override
    public VerifyResult verify(String providerReference) {
        requireConfigured();
        String raw = rawGet("/transactions/" + safeReference(providerReference) + "/verify");
        try {
            JsonNode root = mapper.readTree(raw);
            JsonNode data = root.path("data");
            String status = data.path("status").asText("unknown");
            boolean paid = "successful".equalsIgnoreCase(status);
            log.info("[flutterwave] verify tx={} status={}", providerReference, status);
            return new VerifyResult(paid, providerReference, paid ? "SUCCESS" : status.toUpperCase(), raw);
        } catch (Exception e) {
            log.error("[flutterwave] could not parse verify response", e);
            return new VerifyResult(false, providerReference, "ERROR", raw);
        }
    }

    @Override
    public RefundResult refund(RefundRequest request) {
        requireConfigured();
        Map<String, Object> body = Map.of("amount", request.amount());
        String raw = rawPost("/transactions/" + safeReference(request.providerReference()) + "/refund", body);
        try {
            JsonNode root = mapper.readTree(raw);
            String status = root.path("status").asText("");
            JsonNode data = root.path("data");
            boolean ok = "success".equalsIgnoreCase(status);
            String id = data.path("id").isNumber() ? String.valueOf(data.path("id").longValue())
                    : request.providerReference();
            if (ok) {
                return new RefundResult(true, id, raw, root.path("message").asText("Refund processed"));
            }
            String message = root.path("message").asText("Flutterwave refund failed");
            log.warn("[flutterwave] refund failed: {}", message);
            return new RefundResult(false, id, raw, message);
        } catch (Exception e) {
            log.error("[flutterwave] could not parse refund response", e);
            return new RefundResult(false, request.providerReference(), raw, "Unparseable Flutterwave refund");
        }
    }

    @Override
    public WebhookEvent parseWebhook(String rawPayload, Map<String, String> headers) {
        try {
            JsonNode root = mapper.readTree(rawPayload);
            String event = root.path("event").asText("");
            JsonNode data = root.path("data");
            boolean paid = "charge.completed".equals(event)
                    && "successful".equalsIgnoreCase(data.path("status").asText(""));
            String txnId = data.path("id").isNumber() ? String.valueOf(data.path("id").longValue()) : null;
            JsonNode amountNode = data.path("amount");
            BigDecimal amount = amountNode.isNumber() ? amountNode.decimalValue() : BigDecimal.ZERO;
            return new WebhookEvent(amount, txnId, paid, rawPayload);
        } catch (Exception e) {
            log.error("[flutterwave] could not parse webhook payload", e);
            return new WebhookEvent(BigDecimal.ZERO, null, false, rawPayload);
        }
    }

    @Override
    public boolean verifyWebhookSignature(String rawPayload, Map<String, String> headers) {
        if (webhookSecret.isBlank()) {
            return false;
        }
        String provided = headers.get("verif-hash");
        return webhookSecret.equals(provided);
    }

    private String rawPost(String path, Map<String, Object> body) {
        try {
            return client.post()
                    .uri(path)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + secretKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException e) {
            log.warn("[flutterwave] POST {} failed status={}", path, e.getStatusCode().value());
            throw new BusinessException("Flutterwave request failed: " + e.getStatusCode(),
                    "PROVIDER_CHARGE_FAILED", e);
        }
    }

    private String rawGet(String path) {
        try {
            return client.get()
                    .uri(path)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + secretKey)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException e) {
            log.warn("[flutterwave] GET {} failed status={}", path, e.getStatusCode().value());
            throw new BusinessException("Flutterwave request failed: " + e.getStatusCode(),
                    "PROVIDER_VERIFY_FAILED", e);
        }
    }

    private void requireConfigured() {
        if (!isConfigured()) {
            throw new BusinessException("Flutterwave is not configured", "PROVIDER_NOT_CONFIGURED", "provider");
        }
    }

    private String safeReference(String reference) {
        if (reference == null || reference.isBlank()) {
            throw new BusinessException("Missing provider reference", "INVALID_PROVIDER_REFERENCE");
        }
        return reference;
    }
}