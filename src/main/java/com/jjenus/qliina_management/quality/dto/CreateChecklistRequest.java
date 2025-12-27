package com.jjenus.qliina_management.quality.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class CreateChecklistRequest {
    @NotBlank(message = "Name is required")
    private String name;
    
    private UUID serviceTypeId;
    
    private UUID garmentTypeId;
    
    @NotNull(message = "Items are required")
    private List<ItemDTO> items;
    
    @Data
    public static class ItemDTO {
        @NotBlank(message = "Description is required")
        private String description;
        
        private Boolean required = true;
        
        private String failureSeverity;
    }
}
