package com.jjenus.qliina_management.employee.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShiftDTO {
    private UUID id;
    private UUID employeeId;
    private String employeeName;
    private UUID shopId;
    private String shopName;
    private LocalDate date;
    private LocalDateTime scheduledStart;
    private LocalDateTime scheduledEnd;
    private LocalDateTime actualStart;
    private LocalDateTime actualEnd;
    private LocalDateTime breakStart;
    private LocalDateTime breakEnd;
    private Integer totalBreakMinutes;
    private Integer totalWorkMinutes;
    private Integer overtimeMinutes;
    private String status;
    private String notes;
}
