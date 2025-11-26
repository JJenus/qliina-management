package com.jjenus.qliina_management.audit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateRetentionPolicyRequest {
    @NotBlank(message = "Entity type is required")
    private String entityType;
    
    @NotNull(message = "Retention days is required")
    private Integer retentionDays;
    
    private Boolean archiveEnabled;
    
    private String archiveLocation;
    
    private Boolean deleteEnabled;
    
    private Integer notificationDaysBefore;
}
