package com.jjenus.qliina_management.inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.math.BigDecimal;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class InventoryItemDetailDTO extends InventoryItemDTO {
    private List<ShopStockSummaryDTO> shopStocks;
    private List<TransactionSummaryDTO> recentTransactions;
    private SupplierSummaryDTO supplier;
    private MetadataDTO metadata;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ShopStockSummaryDTO {
        private UUID shopId;
        private String shopName;
        private BigDecimal quantity;
        private String status;
        private LocalDateTime lastRestocked;
        private String locationDetails;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransactionSummaryDTO {
        private UUID id;
        private String type;
        private BigDecimal quantity;
        private LocalDateTime transactionDate;
        private String reason;
        private String performedBy;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SupplierSummaryDTO {
        private UUID id;
        private String name;
        private String contactPerson;
        private String phone;
        private String email;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MetadataDTO {
        private UUID createdBy;
        private LocalDateTime createdAt;
        private UUID updatedBy;
        private LocalDateTime updatedAt;
    }
}
