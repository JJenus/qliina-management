package com.jjenus.qliina_management.billing.dto;

import com.jjenus.qliina_management.billing.model.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record PaymentDTO(
        UUID id,
        UUID invoiceId,
        String gateway,
        String gatewayTransactionId,
        BigDecimal amount,
        BigDecimal feeAmount,
        BigDecimal netAmount,
        PaymentStatus status,
        LocalDateTime paidAt
) {
}
