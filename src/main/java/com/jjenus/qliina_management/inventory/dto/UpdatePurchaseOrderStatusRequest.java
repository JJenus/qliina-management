package com.jjenus.qliina_management.inventory.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdatePurchaseOrderStatusRequest {
    @NotNull(message = "Status is required")
    private String status;
    
    private String notes;
}
