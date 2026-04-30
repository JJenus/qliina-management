package com.jjenus.qliina_management.business.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class SetPricingRequest {
    @NotNull(message = "Service type ID is required")
    private UUID serviceTypeId;
    @NotNull(message = "Garment type ID is required")
    private UUID garmentTypeId;
    @NotNull(message = "Price is required")
    private BigDecimal price;
}