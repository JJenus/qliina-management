package com.jjenus.qliina_management.employee.dto;

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
public class TimesheetDTO {
    private UUID employeeId;
    private String employeeName;
    private PeriodDTO period;
    private List<ShiftDTO> entries;
    private SummaryDTO summary;
    
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
    public static class SummaryDTO {
        private Integer totalScheduledMinutes;
        private Integer totalWorkedMinutes;
        private Integer totalBreakMinutes;
        private Integer totalOvertimeMinutes;
        private BigDecimal regularPay;
        private BigDecimal overtimePay;
        private BigDecimal totalPay;
        private Double hourlyRate;
    }
}
