package com.jjenus.qliina_management.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;
import java.util.List;

@Data
public class UpdateItemStatusRequest {
    @NotNull(message = "Item ID is required")
    private UUID itemId;
    
    @NotBlank(message = "Status is required")
    private String status;
    
    private String notes;
    
    private List<String> attachments;
}
