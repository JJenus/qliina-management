package com.jjenus.qliina_management.inventory.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class UpdateInventoryItemRequest {
    private String name;
    private String description;
    private String category;
    private String unit;
    private Integer reorderLevel;
    private Integer reorderQuantity;
    private BigDecimal unitPrice;
    private UUID supplierId;
    private Boolean isActive;
    private BigDecimal minStockLevel;
    private BigDecimal maxStockLevel;
    private String location;
}
