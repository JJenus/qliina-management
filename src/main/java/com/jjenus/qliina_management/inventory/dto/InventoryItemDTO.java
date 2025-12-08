package com.jjenus.qliina_management.inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.experimental.SuperBuilder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryItemDTO {
    private UUID id;
    private String sku;
    private String name;
    private String description;
    private String category;
    private String unit;
    private Integer reorderLevel;
    private Integer reorderQuantity;
    private BigDecimal currentStock;
    private BigDecimal unitPrice;
    private UUID supplierId;
    private String supplierName;
    private Boolean isActive;
    private BigDecimal minStockLevel;
    private BigDecimal maxStockLevel;
    private String location;
    private String stockStatus;
}
