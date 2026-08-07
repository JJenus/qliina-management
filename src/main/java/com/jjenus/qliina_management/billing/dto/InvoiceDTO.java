package com.jjenus.qliina_management.billing.dto;

import com.jjenus.qliina_management.billing.model.InvoiceStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record InvoiceDTO(
        UUID id,
        UUID subscriptionId,
        String invoiceNumber,
        BigDecimal amount,
        InvoiceStatus status,
        LocalDateTime issuedAt,
        LocalDateTime dueAt,
        List<InvoiceLineItemDTO> lineItems
) {
}
