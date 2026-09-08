package com.jjenus.qliina_management.payment.controller;

import com.jjenus.qliina_management.payment.service.PaymentProviderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Inbound payment-provider webhooks. Registered under {@code /webhooks/payments}
 * (not the billing gateway path) and covered by the existing
 * {@code /api/v1/webhooks/**} permitAll rule — the JWT can never reach these
 * endpoints; trust comes from signature verification in each provider.
 */
@Slf4j
@Tag(name = "Payment Provider Webhooks", description = "Provider notification endpoints (signature-verified)")
@RestController
@RequestMapping("/api/v1/webhooks/payments")
@RequiredArgsConstructor
public class PaymentProviderWebhookController {

    private final PaymentProviderService paymentProviderService;

    @Operation(
        summary = "Receive a provider webhook",
        description = "Signature-verified settlement notification (e.g. charge.success / charge.completed)"
    )
    @PostMapping(value = "/{provider}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> handleWebhook(
            @PathVariable String provider,
            @RequestBody String rawPayload,
            @RequestHeader Map<String, String> headers) {
        paymentProviderService.processWebhook(provider, rawPayload, new HashMap<>(headers));
        return ResponseEntity.ok(Map.of("status", "received"));
    }
}