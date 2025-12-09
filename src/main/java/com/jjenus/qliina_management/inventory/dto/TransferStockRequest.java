package com.jjenus.qliina_management.inventory.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class TransferStockRequest {
    @NotNull(message = "Source shop ID is required")
    private UUID sourceShopId;
    
    @NotNull(message = "Target shop ID is required")
    private UUID targetShopId;
    
    @NotNull(message = "Item ID is required")
    private UUID itemId;
    
    @NotNull(message = "Quantity is required")
    private BigDecimal quantity;
    
    private String reason;
    
    private String notes;
}
