package com.jjenus.qliina_management.billing.dto;

import com.jjenus.qliina_management.billing.model.LineItemType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record InvoiceLineItemDTO(
        LineItemType type,
        String description,
        BigDecimal amount,
        LocalDateTime periodStart,
        LocalDateTime periodEnd
) {
}
