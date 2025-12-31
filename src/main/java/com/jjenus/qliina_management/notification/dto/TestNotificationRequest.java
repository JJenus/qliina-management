package com.jjenus.qliina_management.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;
import java.util.UUID;

@Data
public class TestNotificationRequest {
    @NotBlank(message = "Recipient is required")
    private String recipient;
    
    @NotNull(message = "Channel is required")
    private String channel;
    
    private UUID templateId;
    
    private Map<String, Object> templateData;
    
    private String customTitle;
    
    private String customBody;
}
