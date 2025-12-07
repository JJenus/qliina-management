package com.jjenus.qliina_management.inventory.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
public class CreatePurchaseOrderRequest {
    @NotNull(message = "Supplier ID is required")
    private UUID supplierId;
    
    @NotNull(message = "Shop ID is required")
    private UUID shopId;
    
    private LocalDate expectedDelivery;
    
    @NotNull(message = "Items are required")
    private List<PurchaseOrderItem> items;
    
    private BigDecimal tax;
    
    private BigDecimal shipping;
    
    private String notes;
    
    private String terms;
    
    @Data
    public static class PurchaseOrderItem {
        @NotNull(message = "Item ID is required")
        private UUID itemId;
        
        @NotNull(message = "Quantity is required")
        private Integer quantity;
        
        @NotNull(message = "Unit price is required")
        private BigDecimal unitPrice;
        
        private String notes;
    }
}
