package com.jjenus.qliina_management.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResultDTO {
    private Boolean success;
    private UUID paymentId;
    private BigDecimal amount;
    private String status;
    private BigDecimal balanceDue;
    private Boolean isFullyPaid;
    private String transactionId;
    private String receiptUrl;
    private BigDecimal change;
    private List<String> errors;
}
