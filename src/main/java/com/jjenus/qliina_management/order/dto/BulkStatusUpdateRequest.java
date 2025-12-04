package com.jjenus.qliina_management.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class BulkStatusUpdateRequest {
    @NotEmpty(message = "Order IDs are required")
    private List<UUID> orderIds;
    
    @NotBlank(message = "Status is required")
    private String status;
    
    private String notes;
    
    private Boolean notifyCustomers;
}