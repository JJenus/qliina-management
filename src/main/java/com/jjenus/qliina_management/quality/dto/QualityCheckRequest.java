package com.jjenus.qliina_management.quality.dto;

import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class QualityCheckRequest {
    private UUID checklistId;
    private List<CheckResultDTO> results;
    private List<ReportDefectRequest> defects;
    private String notes;
    private List<String> images;
    
    @Data
    public static class CheckResultDTO {
        private UUID checklistItemId;
        private Boolean passed;
        private String notes;
    }
}
