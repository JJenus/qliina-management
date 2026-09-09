package com.jjenus.qliina_management.payment.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * Links an unmatched-funds queue item to a specific order (and thus business).
 * The notes field captures why the money was matched (e.g. the reference the
 * customer typed, or the invoice that covers it).
 */
@Data
public class ResolveReconciliationRequest {
    @NotNull(message = "Order is required")
    private UUID orderId;

    private String notes;
}