package com.jjenus.qliina_management.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A staff-facing reconciliation queue item: a signed, settled provider webhook
 * that arrived with no matching pending/known {@code OrderPayment} (e.g. an
 * external direct bank transfer into a BYO account). OPEN globally until an
 * admin links it to a business + order.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentReconciliationItemDTO {
    private UUID id;
    private String provider;
    private String providerReference;
    private BigDecimal amount;
    private String status;
    private LocalDateTime receivedAt;
    private UUID businessId;
    private UUID orderId;
    private String notes;
    private UUID resolvedBy;
    private LocalDateTime resolvedAt;
}