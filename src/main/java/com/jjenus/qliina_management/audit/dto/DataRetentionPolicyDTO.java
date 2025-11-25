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
public class DataRetentionPolicyDTO {
    private UUID id;
    private String entityType;
    private Integer retentionDays;
    private Boolean archiveEnabled;
    private String archiveLocation;
    private Boolean deleteEnabled;
    private Integer notificationDaysBefore;
    private LocalDateTime lastRun;
    private LocalDateTime nextRun;
    private Boolean isActive;
}
