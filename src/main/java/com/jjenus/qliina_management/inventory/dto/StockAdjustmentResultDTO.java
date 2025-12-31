package com.jjenus.qliina_management.inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockAdjustmentResultDTO {
    private UUID shopId;
    private List<AdjustmentResult> results;
    private List<StockAlertDTO> generatedAlerts;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdjustmentResult {
        private UUID itemId;
        private String itemName;
        private BigDecimal previousQuantity;
        private BigDecimal newQuantity;
        private BigDecimal adjustment;
        private boolean success;
        private String message;
    }
}
