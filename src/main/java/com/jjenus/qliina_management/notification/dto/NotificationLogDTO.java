package com.jjenus.qliina_management.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationLogDTO {
    private UUID id;
    private UUID notificationId;
    private String notificationType;
    private String recipient;
    private String channel;
    private String status;
    private String subject;
    private String errorMessage;
    private LocalDateTime sentAt;
    private LocalDateTime deliveredAt;
    private Integer retryCount;
}
