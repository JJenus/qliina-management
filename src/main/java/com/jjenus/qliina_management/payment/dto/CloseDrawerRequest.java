package com.jjenus.qliina_management.payment.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class CloseDrawerRequest {
    @NotNull(message = "Session ID is required")
    private UUID sessionId;
    
    @NotNull(message = "Actual cash is required")
    private Double actualCash;
    
    @NotNull(message = "Second person verification is required")
    private UUID countedBy;
    
    private String notes;
}
