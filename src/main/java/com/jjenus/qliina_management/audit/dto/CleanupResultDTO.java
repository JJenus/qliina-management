package com.jjenus.qliina_management.audit.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CleanupResultDTO {
    private Map<String, Long> recordsDeleted;
    private Map<String, Long> recordsArchived;
    private Long totalRecordsProcessed;
    private Long executionTimeMs;
    private String status;
    private String message;
}
