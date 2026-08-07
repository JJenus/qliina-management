package com.jjenus.qliina_management.billing.gateway;

import java.math.BigDecimal;

/**
 * Canonical gateway webhook event. Webhook redelivery is routine (§8); the
 * billing engine reconciles on {@link #transactionId()} and dedupes.
 */
public record GatewayWebhookEvent(String eventType, String transactionId,
                                  BigDecimal amount, boolean paid,
                                  String rawPayload) {

    public boolean isChargeSucceeded() {
        return "charge.succeeded".equals(eventType) && paid;
    }

    public boolean isChargeFailed() {
        return "charge.failed".equals(eventType);
    }
}
