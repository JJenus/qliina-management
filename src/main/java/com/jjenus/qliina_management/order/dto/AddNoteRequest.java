package com.jjenus.qliina_management.order.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.util.List;

@Data
@Builder
public class AddNoteRequest {
    @NotBlank(message = "Content is required")
    private String content;
    
    private String type;
    
    private Boolean isCustomerVisible;
    
    private List<String> attachments;
}
