package com.jjenus.qliina_management.payment.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class PaymentFilter {
    private UUID orderId;
    private UUID customerId;
    private UUID shopId;
    private String method;
    private String status;
    private LocalDateTime fromDate;
    private LocalDateTime toDate;
    private UUID collectedBy;
    private Double minAmount;
    private Double maxAmount;
}
