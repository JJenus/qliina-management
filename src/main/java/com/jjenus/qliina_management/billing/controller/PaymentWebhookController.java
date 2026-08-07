package com.jjenus.qliina_management.billing.controller;

import com.jjenus.qliina_management.billing.service.BillingPaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Gateway webhook ingestion. Public endpoint, signature-verified per gateway
 * (§8). Redelivery is routine — reconciliation dedupes on gateway transaction id.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class PaymentWebhookController {

    private final BillingPaymentService paymentService;

    @PostMapping("/api/v1/webhooks/payment/{gateway}")
    public ResponseEntity<Map<String, String>> handleWebhook(
            @PathVariable String gateway,
            @RequestBody String rawPayload,
            @RequestHeader Map<String, String> headers) {
        paymentService.handleWebhook(gateway, rawPayload, new HashMap<>(headers));
        Map<String, String> response = new HashMap<>();
        response.put("status", "received");
        return ResponseEntity.ok(response);
    }
}
