package com.jjenus.qliina_management.payment.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class OpenDrawerRequest {
    @NotNull(message = "Shop ID is required")
    private UUID shopId;
    
    @NotNull(message = "Starting cash is required")
    private Double startingCash;
    
    private String notes;
}
