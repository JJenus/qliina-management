package com.jjenus.qliina_management.order.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class UpdateStatusRequest {
    @NotBlank(message = "Status is required")
    private String status;
    
    private String notes;
    
    private List<String> attachments;
    
    private Boolean notifyCustomer;
}
