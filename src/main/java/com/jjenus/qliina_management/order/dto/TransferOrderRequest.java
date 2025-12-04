package com.jjenus.qliina_management.order.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class TransferOrderRequest {
    @NotNull(message = "Target shop ID is required")
    private UUID targetShopId;
    
    @NotNull(message = "Reason is required")
    private String reason;
    
    private String transferNotes;
    
    private Boolean notifyCustomer;
}
