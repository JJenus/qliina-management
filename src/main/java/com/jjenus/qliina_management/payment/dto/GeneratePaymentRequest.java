package com.jjenus.qliina_management.payment.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Ask a configured provider to start a hosted checkout for an order balance
 * (or part of it). The business hands the resulting link/QR to the customer,
 * who completes the payment on the processor's own page (PENDING until the
 * webhook or a manual recheck confirms settlement).
 */
@Data
public class GeneratePaymentRequest {
    /**
     * Optional; defaults to the order's current balance due (total − completed).
     * Must be positive and must not exceed the balance due.
     */
    private Double amount;

    /**
     * Optional; defaults to the provider's first supported method.
     */
    private String method;

    @NotBlank(message = "Provider is required")
    private String provider;
}