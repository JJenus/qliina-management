package com.jjenus.qliina_management.order.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CancelOrderRequest {
    @NotNull(message = "Reason is required")
    private String reason;
    
    private String notes;
}
