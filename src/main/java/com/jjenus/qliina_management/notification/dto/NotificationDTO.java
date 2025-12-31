package com.jjenus.qliina_management.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDTO {
    private UUID id;
    private UUID userId;
    private String type;
    private String channel;
    private String title;
    private String body;
    private Map<String, Object> data;
    private String status;
    private String priority;
    private LocalDateTime scheduledFor;
    private LocalDateTime createdAt;
    private LocalDateTime deliveredAt;
    private Boolean isRead;
}
