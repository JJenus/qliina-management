package com.jjenus.qliina_management.audit.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class AuditLogFilter {
    private UUID userId;
    private String entityType;
    private UUID entityId;
    private String action;
    private String category;
    private String severity;
    private LocalDateTime fromDate;
    private LocalDateTime toDate;
    private UUID shopId;
    private String ipAddress;
}
