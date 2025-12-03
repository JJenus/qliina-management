package com.jjenus.qliina_management.order.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
public class UpdateOrderRequest {
    private List<UpdateOrderItemRequest> items;
    private String priority;
    private LocalDateTime promisedDate;
    private DeliveryInfoDTO delivery;
    private String notes;
    private List<String> tags;
    
    @Data
    public static class UpdateOrderItemRequest {
        private UUID id;
        private Integer quantity;
        private Double weight;
        private Double unitPrice;
        private String specialInstructions;
    }
    
    @Data
    public static class DeliveryInfoDTO {
        private String type;
        private UUID addressId;
        private LocalDateTime scheduledTime;
    }
}
