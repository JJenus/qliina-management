package com.jjenus.qliina_management.payment.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
public class GenerateInvoiceRequest {
    private UUID accountId;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private LocalDate dueDate;
    private List<UUID> includeOrders;
    private Boolean sendEmail;
}
