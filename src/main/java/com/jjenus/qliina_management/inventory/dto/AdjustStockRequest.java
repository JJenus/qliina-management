package com.jjenus.qliina_management.inventory.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdjustStockRequest {
    @NotNull(message = "Shop ID is required")
    private UUID shopId;
    
    @NotNull(message = "Adjustments are required")
    private List<StockAdjustment> adjustments;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StockAdjustment {
        @NotNull(message = "Item ID is required")
        private UUID itemId;
        
        @NotNull(message = "Quantity is required")
        private BigDecimal quantity;
        
        @NotNull(message = "Reason is required")
        private String reason;

        @NotNull(message = "Reason is required")
        private AdjustStockAction action;
        
        private String reference;
        
        private String notes;
    }
}
