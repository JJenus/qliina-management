package com.jjenus.qliina_management.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class CreateInventoryItemRequest {
    @NotBlank(message = "SKU is required")
    private String sku;
    
    @NotBlank(message = "Name is required")
    private String name;
    
    private String description;
    
    @NotBlank(message = "Category is required")
    private String category;
    
    @NotBlank(message = "Unit is required")
    private String unit;
    
    @NotNull(message = "Reorder level is required")
    private Integer reorderLevel;
    
    @NotNull(message = "Reorder quantity is required")
    private Integer reorderQuantity;
    
    private BigDecimal unitPrice;
    
    private UUID supplierId;
    
    private BigDecimal minStockLevel;
    
    private BigDecimal maxStockLevel;
    
    private String location;
}
