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
public class ComplianceReportDTO {
    private UUID id;
    private String reportNumber;
    private String reportType;
    private LocalDateTime periodStart;
    private LocalDateTime periodEnd;
    private LocalDateTime generatedAt;
    private String generatedBy;
    private Object reportData;
    private String fileUrl;
    private Long fileSize;
    private String status;
}
