package com.jjenus.qliina_management.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Result of {@code POST /payments/orders/{orderId}/generate}: a PENDING
 * {@code OrderPayment} plus the hosted checkout URL (also encoded in the QR
 * payload) that the business can show the customer.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeneratePaymentResultDTO {
    private UUID paymentId;
    private UUID orderId;
    private String orderNumber;
    private BigDecimal amount;
    private String method;
    private String reference;
    private String provider;
    private String providerReference;
    private String status;
    private String checkoutUrl;
    private String qrPayload;
    private BigDecimal balanceDue;
    private Boolean isFullyPaid;
}