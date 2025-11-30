package com.jjenus.qliina_management.customer.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AdjustPointsRequest {
    @NotNull(message = "Points are required")
    private Integer points;
    
    @NotNull(message = "Reason is required")
    private String reason;
    
    @NotNull(message = "Source is required")
    private String source;
}
