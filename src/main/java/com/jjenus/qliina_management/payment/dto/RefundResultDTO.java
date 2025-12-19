package com.jjenus.qliina_management.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundResultDTO {
    private UUID refundId;
    private UUID originalPaymentId;
    private BigDecimal amount;
    private String status;
    private LocalDateTime processedAt;
    private String receiptUrl;
    private BigDecimal newBalance;
}
