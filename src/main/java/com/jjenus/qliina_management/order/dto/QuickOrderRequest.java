package com.jjenus.qliina_management.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class QuickOrderRequest {
    @NotBlank(message = "Customer phone is required")
    private String customerPhone;
    
    @NotNull(message = "Shop ID is required")
    private UUID shopId;
    
    @NotBlank(message = "Service type is required")
    private String serviceType;
    
    @NotNull(message = "Item count is required")
    private Integer itemCount;
    
    @NotNull(message = "Total amount is required")
    private Double totalAmount;
    
    private String paymentMethod;
    
    private String notes;
}
