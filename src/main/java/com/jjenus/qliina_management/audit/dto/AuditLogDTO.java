package com.jjenus.qliina_management.audit.dto;

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
public class AuditLogDTO {
    private UUID id;
    private LocalDateTime timestamp;
    private UUID userId;
    private String userName;
    private String userEmail;
    private UUID businessId;
    private UUID shopId;
    private String entityType;
    private UUID entityId;
    private String entityDisplay;
    private String action;
    private String category;
    private String severity;
    private Map<String, Object> oldValue;
    private Map<String, Object> newValue;
    private Map<String, ChangeDetailDTO> changes;
    private String ipAddress;
    private String userAgent;
    private String deviceId;
    private String sessionId;
    private String requestId;
    private String requestPath;
    private String requestMethod;
    private Integer responseStatus;
    private Long executionTimeMs;
    private String details;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChangeDetailDTO {
        private String field;
        private Object oldValue;
        private Object newValue;
        private String changeType;
    }
}
