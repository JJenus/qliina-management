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

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * Paystack integration for the POS checkout.
 *
 * <p>Uses the standard redirect model: {@code charge()} calls
 * {@code POST /transaction/initialize} (hosted authorization page), the
 * merchant redirects the customer, then settlement is confirmed via
 * {@code GET /transaction/verify/{reference}} or the {@code charge.success}
 * webhook.
 *
 * <p>Amounts are converted to kobo (×100) exactly as Paystack expects.
 * Fail-closed: with no secret the provider is not configured and refuses to
 * charge / verify / refund.
 */
@Slf4j
@Component
public class PaystackPaymentProvider implements PaymentProvider {

    public static final String PROVIDER_NAME = "paystack";
    private static final String BASE_URL = "https://api.paystack.co";
    private static final BigDecimal MINOR_UNIT = new BigDecimal("100");
    private static final List<String> METHODS = List.of("CARD", "TRANSFER");

    private final RestClient client;
    private final ObjectMapper mapper;
    private final String secretKey;

    public PaystackPaymentProvider(@Value("${app.payments.providers.paystack.secret-key:}") String secretKey,
                                   ObjectMapper mapper) {
        this.secretKey = secretKey == null ? "" : secretKey.trim();
        this.mapper = mapper;
        this.client = RestClient.create(BASE_URL);
    }

    @Override
    public String getName() {
        return PROVIDER_NAME;
    }

    @Override
    public String getDisplayName() {
        return "Paystack";
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
    public boolean supportsPlatformSubaccounts() {
        return true;
    }

    @Override
    public ChargeResult charge(ChargeRequest request) {
        String secret = effectiveSecret(request);
        if (secret.isBlank()) {
            requireConfigured();
        }
        long amountKobo = request.amount().multiply(MINOR_UNIT).setScale(0, RoundingMode.HALF_UP).longValueExact();

        Map<String, Object> body = new java.util.HashMap<>();
        body.put("email", request.customerEmail() == null ? "" : request.customerEmail());
        body.put("amount", amountKobo);
        body.put("currency", request.currency());
        body.put("reference", request.reference());
        if (request.connection() != null && request.connection().platformSubaccountId() != null
                && !request.connection().platformSubaccountId().isBlank()) {
            body.put("subaccount", request.connection().platformSubaccountId());
        }
        String raw = rawPost("/transaction/initialize", body, secret);
        return parseInitialize(raw);
    }

    /** BYO charges authenticate with the business's own secret, not the platform key. */
    private String effectiveSecret(ChargeRequest request) {
        if (request.connection() != null && request.connection().credentials() != null
                && !request.connection().credentials().isBlank()) {
            return request.connection().credentials();
        }
        return secretKey;
    }

    private ChargeResult parseInitialize(String raw) {
        try {
            JsonNode root = mapper.readTree(raw);
            boolean ok = root.path("status").asBoolean();
            JsonNode data = root.path("data");
            String reference = data.path("reference").asText(null);
            String checkoutUrl = data.path("authorization_url").asText(null);
            if (ok && reference != null && checkoutUrl != null) {
                log.info("[paystack] initialize OK reference={}", reference);
                return new ChargeResult(false, reference, checkoutUrl, BigDecimal.ZERO,
                        "PENDING", raw, "Awaiting customer authorization");
            }
            String message = root.path("message").asText("Paystack initialize failed");
            log.warn("[paystack] initialize failed: {}", message);
            return new ChargeResult(false, reference, null, BigDecimal.ZERO, "DECLINED", raw, message);
        } catch (Exception e) {
            log.error("[paystack] could not parse initialize response", e);
            return new ChargeResult(false, null, null, BigDecimal.ZERO, "DECLINED", raw,
                    "Unparseable Paystack response");
        }
    }

    @Override
    public VerifyResult verify(String providerReference) {
        requireConfigured();
        String raw = rawGet("/transaction/verify/" + safeReference(providerReference));
        try {
            JsonNode root = mapper.readTree(raw);
            JsonNode data = root.path("data");
            String status = data.path("status").asText("unknown");
            boolean paid = "success".equalsIgnoreCase(status);
            log.info("[paystack] verify reference={} status={}", providerReference, status);
            return new VerifyResult(paid, providerReference, paid ? "SUCCESS" : status.toUpperCase(), raw);
        } catch (Exception e) {
            log.error("[paystack] could not parse verify response", e);
            return new VerifyResult(false, providerReference, "ERROR", raw);
        }
    }

    @Override
    public RefundResult refund(RefundRequest request) {
        requireConfigured();
        long amountKobo = request.amount().multiply(MINOR_UNIT).setScale(0, RoundingMode.HALF_UP).longValueExact();
        Map<String, Object> body = Map.of(
                "transaction", request.providerReference(),
                "amount", amountKobo);
        String raw = rawPost("/refund", body);
        try {
            JsonNode root = mapper.readTree(raw);
            boolean ok = root.path("status").asBoolean();
            JsonNode data = root.path("data");
            String reference = data.path("reference").asText(request.providerReference());
            if (ok) {
                return new RefundResult(true, reference, raw, root.path("message").asText("Refund submitted"));
            }
            String message = root.path("message").asText("Paystack refund failed");
            log.warn("[paystack] refund failed: {}", message);
            return new RefundResult(false, reference, raw, message);
        } catch (Exception e) {
            log.error("[paystack] could not parse refund response", e);
            return new RefundResult(false, request.providerReference(), raw, "Unparseable Paystack refund");
        }
    }

    @Override
    public WebhookEvent parseWebhook(String rawPayload, Map<String, String> headers) {
        try {
            JsonNode root = mapper.readTree(rawPayload);
            String event = root.path("event").asText("");
            JsonNode data = root.path("data");
            boolean paid = "charge.success".equals(event);
            boolean failed = "charge.failed".equals(event);
            String reference = data.path("reference").asText(null);
            JsonNode amountNode = data.path("amount");
            BigDecimal amount = amountNode.isNumber() ? amountNode.decimalValue()
                    .divide(MINOR_UNIT, 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
            return new WebhookEvent(amount, reference, paid, failed, rawPayload);
        } catch (Exception e) {
            log.error("[paystack] could not parse webhook payload", e);
            return new WebhookEvent(BigDecimal.ZERO, null, false, false, rawPayload);
        }
    }

    @Override
    public boolean verifyWebhookSignature(String rawPayload, Map<String, String> headers) {
        if (secretKey.isBlank()) {
            return false;
        }
        String signature = headers.get("x-paystack-signature");
        if (signature == null) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            byte[] digest = mac.doFinal(rawPayload.getBytes(StandardCharsets.UTF_8));
            return MessageDigest.isEqual(HexFormat.of().formatHex(digest).getBytes(StandardCharsets.US_ASCII),
                    signature.getBytes(StandardCharsets.US_ASCII));
        } catch (Exception e) {
            log.error("[paystack] signature verification failed", e);
            return false;
        }
    }

    private String rawPost(String path, Map<String, Object> body) {
        return rawPost(path, body, secretKey);
    }

    private String rawPost(String path, Map<String, Object> body, String secret) {
        try {
            return client.post()
                    .uri(path)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + secret)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException e) {
            log.warn("[paystack] POST {} failed status={}", path, e.getStatusCode().value());
            throw new BusinessException("Paystack request failed: " + e.getStatusCode(),
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
            log.warn("[paystack] GET {} failed status={}", path, e.getStatusCode().value());
            throw new BusinessException("Paystack request failed: " + e.getStatusCode(),
                    "PROVIDER_VERIFY_FAILED", e);
        }
    }

    private void requireConfigured() {
        if (!isConfigured()) {
            throw new BusinessException("Paystack is not configured", "PROVIDER_NOT_CONFIGURED", "provider");
        }
    }

    private String safeReference(String reference) {
        if (reference == null || reference.isBlank()) {
            throw new BusinessException("Missing provider reference", "INVALID_PROVIDER_REFERENCE");
        }
        return reference;
    }
}