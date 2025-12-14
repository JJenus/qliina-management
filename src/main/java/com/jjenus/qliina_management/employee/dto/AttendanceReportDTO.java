package com.jjenus.qliina_management.employee.dto;

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
public class AttendanceReportDTO {
    private UUID shopId;
    private String shopName;
    private PeriodDTO period;
    private SummaryDTO summary;
    private List<DailyAttendanceDTO> dailyBreakdown;
    private List<EmployeeAttendanceDTO> employeeBreakdown;
    
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
        private Long totalEmployees;
        private Double averageAttendance;
        private Long totalAbsences;
        private Long totalLates;
        private Double averageOvertime;
        private Map<String, Long> statusBreakdown;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyAttendanceDTO {
        private LocalDate date;
        private Long present;
        private Long absent;
        private Long late;
        private Long onLeave;
        private Double attendanceRate;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EmployeeAttendanceDTO {
        private UUID employeeId;
        private String employeeName;
        private Integer presentDays;
        private Integer absentDays;
        private Integer lateDays;
        private Double attendanceRate;
        private Double averageOvertime;
    }
}
