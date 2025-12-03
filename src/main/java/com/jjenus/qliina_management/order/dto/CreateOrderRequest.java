package com.jjenus.qliina_management.order.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderRequest {
    @NotNull(message = "Customer ID is required")
    private UUID customerId;
    
    @NotNull(message = "Shop ID is required")
    private UUID shopId;
    
    @NotNull(message = "Items are required")
    private List<ItemDTO> items;
    
    private List<DiscountDTO> discounts;
    
    private String priority;
    
    private LocalDateTime promisedDate;
    
    private LocalDateTime expectedReadyAt;
    
    private DeliveryDTO delivery;
    
    private String notes;
    
    private List<String> tags;
    
    private Boolean sendNotifications;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ItemDTO {
        @NotNull(message = "Service type ID is required")
        private UUID serviceTypeId;
        
        private UUID garmentTypeId;
        
        private String description;
        
        @NotNull(message = "Quantity is required")
        private Integer quantity;
        
        private Double weight;
        
        @NotNull(message = "Unit price is required")
        private Double unitPrice;
        
        private String specialInstructions;
        
        private List<String> images;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DiscountDTO {
        private String type;
        private Double value;
        private String reason;
        private String promoCode;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeliveryDTO {
        private String type;
        private UUID addressId;
        private LocalDateTime scheduledTime;
    }
}
