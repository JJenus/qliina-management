package com.jjenus.qliina_management.inventory.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class AdjustStockRequest {
    @NotNull(message = "Shop ID is required")
    private UUID shopId;
    
    @NotNull(message = "Adjustments are required")
    private List<StockAdjustment> adjustments;
    
    @Data
    @Builder
    public static class StockAdjustment {
        @NotNull(message = "Item ID is required")
        private UUID itemId;
        
        @NotNull(message = "Quantity is required")
        private BigDecimal quantity;
        
        @NotNull(message = "Reason is required")
        private String reason;
        
        private String reference;
        
        private String notes;
    }
}
