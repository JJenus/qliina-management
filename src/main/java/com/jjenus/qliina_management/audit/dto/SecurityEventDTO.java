package com.jjenus.qliina_management.audit.dto;

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
public class SecurityEventDTO {
    private UUID id;
    private LocalDateTime timestamp;
    private String eventType;
    private String severity;
    private UUID userId;
    private String username;
    private String ipAddress;
    private String userAgent;
    private String location;
    private String details;
    private Boolean blocked;
    private String blockReason;
    private LocalDateTime resolvedAt;
    private String resolvedBy;
}
