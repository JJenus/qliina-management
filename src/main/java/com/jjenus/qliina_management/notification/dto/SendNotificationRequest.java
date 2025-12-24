package com.jjenus.qliina_management.notification.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
public class SendNotificationRequest {
    private List<UUID> recipients;
    
    @NotNull(message = "Notification type is required")
    private String type;
    
    @NotNull(message = "Channel is required")
    private String channel;
    
    private String title;
    
    private String body;
    
    private UUID templateId;
    
    private Map<String, Object> templateData;
    
    private LocalDateTime scheduledFor;
    
    private String priority;
}
