package com.jjenus.qliina_management.quality.dto;

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
public class QualityCheckResultDTO {
    private UUID itemId;
    private UUID checklistId;
    private String status;
    private List<CheckResultItemDTO> checkResults;
    private List<DefectDTO> defects;
    private String checkedBy;
    private LocalDateTime checkedAt;
    private String nextAction;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CheckResultItemDTO {
        private String item;
        private Boolean passed;
        private String notes;
    }
}
