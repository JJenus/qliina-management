package com.jjenus.qliina_management.quality.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeScorecardDTO {
    private UUID employeeId;
    private String employeeName;
    private PeriodDTO period;
    private Integer itemsProcessed;
    private Integer itemsPassed;
    private Integer itemsFailed;
    private Double passRate;
    private Double reworkRate;
    private List<DefectTypeCountDTO> defectsByType;
    private Map<String, Long> severityBreakdown;
    private List<TrendDTO> trend;
    private Integer rank;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PeriodDTO {
        private LocalDate start;
        private LocalDate end;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DefectTypeCountDTO {
        private String type;
        private Integer count;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrendDTO {
        private LocalDate date;
        private Double passRate;
    }
}
