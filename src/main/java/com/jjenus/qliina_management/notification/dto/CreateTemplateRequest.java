package com.jjenus.qliina_management.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class CreateTemplateRequest {
    @NotBlank(message = "Template name is required")
    private String name;
    
    private String description;
    
    @NotNull(message = "Notification type is required")
    private String type;
    
    @NotNull(message = "Channel is required")
    private String channel;
    
    private String subject;
    
    private String titleTemplate;
    
    @NotBlank(message = "Body template is required")
    private String bodyTemplate;
    
    private List<String> variables;
}
