package com.jjenus.qliina_management.billing.dto;

public record PlanFeatureDTO(
        String featureKey,
        String value,
        boolean isHardLimit
) {
}
