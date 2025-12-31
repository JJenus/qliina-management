package com.jjenus.qliina_management.reporting.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmployeePerfDTO {
    private UUID employeeId;
    private String employeeName;
    private String role;
    private PeriodDTO period;
    private MetricsDTO metrics;
    private Integer rank;
    private List<DailyMetricDTO> dailyBreakdown;
    
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
    public static class MetricsDTO {
        private Integer ordersProcessed;
        private Integer itemsProcessed;
        private BigDecimal revenueHandled;
        private Double qualityScore;
        private Double attendanceRate;
        private Double ontimeRate;
        private Double productivity; // items per hour
        private Double targetAchievement;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyMetricDTO {
        private LocalDate date;
        private Integer orders;
        private Integer items;
        private Double quality;
        private Double hoursWorked;
    }
}
