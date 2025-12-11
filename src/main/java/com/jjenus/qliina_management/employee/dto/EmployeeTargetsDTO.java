package com.jjenus.qliina_management.employee.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeTargetsDTO {
    private UUID employeeId;
    private String employeeName;
    private List<TargetDTO> targets;
    private AchievementSummaryDTO summary;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TargetDTO {
        private LocalDate date;
        private String metric;
        private Integer target;
        private Integer actual;
        private Boolean achieved;
        private Double achievementRate;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AchievementSummaryDTO {
        private Double overallAchievement;
        private Integer targetsAchieved;
        private Integer totalTargets;
        private List<MetricAchievementDTO> byMetric;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MetricAchievementDTO {
        private String metric;
        private Double achievementRate;
        private Integer achieved;
        private Integer total;
    }
}
