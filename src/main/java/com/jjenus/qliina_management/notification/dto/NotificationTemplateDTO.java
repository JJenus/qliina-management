package com.jjenus.qliina_management.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationTemplateDTO {
    private UUID id;
    private String name;
    private String description;
    private String type;
    private String channel;
    private String subject;
    private String titleTemplate;
    private String bodyTemplate;
    private List<String> variables;
    private Boolean isActive;
    private LocalDateTime createdAt;
}
