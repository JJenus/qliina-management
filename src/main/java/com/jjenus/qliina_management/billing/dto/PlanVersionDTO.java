package com.jjenus.qliina_management.billing.dto;

import com.jjenus.qliina_management.billing.model.PlanStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record PlanVersionDTO(
        UUID id,
        java.math.BigDecimal price,
        String currency,
        LocalDateTime effectiveFrom
) {
}
