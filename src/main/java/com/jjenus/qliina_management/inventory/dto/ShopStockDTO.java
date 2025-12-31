package com.jjenus.qliina_management.inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShopStockDTO {
    private UUID shopId;
    private String shopName;
    private List<ShopStockItemDTO> items;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ShopStockItemDTO {
        private UUID itemId;
        private String itemName;
        private String sku;
        private BigDecimal quantity;
        private String unit;
        private LocalDateTime lastRestocked;
        private String status;
        private String locationDetails;
        private BigDecimal reorderLevel;
        private Integer suggestedOrder;
    }
}
