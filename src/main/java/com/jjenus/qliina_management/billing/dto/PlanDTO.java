package com.jjenus.qliina_management.billing.dto;

import com.jjenus.qliina_management.billing.model.PlanStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record PlanDTO(
        UUID id,
        String name,
        String description,
        PlanStatus status,
        LocalDateTime archivedAt,
        List<PlanVersionDTO> versions,
        List<PlanFeatureDTO> features
) {
}
