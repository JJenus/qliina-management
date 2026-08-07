package com.jjenus.qliina_management.billing.dto;

import com.jjenus.qliina_management.billing.model.PaymentMethodType;

import java.util.UUID;

public record BillingPaymentMethodDTO(
        UUID id,
        UUID businessId,
        PaymentMethodType type,
        boolean isDefault,
        String label
) {
}
