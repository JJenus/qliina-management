package com.jjenus.qliina_management.inventory.dto;

import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class ReceivePurchaseOrderRequest {
    private List<ReceivedItem> items;
    private String notes;
    
    @Data
    public static class ReceivedItem {
        private UUID itemId;
        private Integer receivedQuantity;
        private String notes;
    }
}
