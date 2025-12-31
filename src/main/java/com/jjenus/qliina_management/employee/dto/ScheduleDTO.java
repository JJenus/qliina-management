package com.jjenus.qliina_management.employee.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleDTO {
    private UUID id;
    private UUID employeeId;
    private String employeeName;
    private UUID shopId;
    private String shopName;
    private LocalDate date;
    private String dayOfWeek;
    private LocalTime startTime;
    private LocalTime endTime;
    private String role;
    private Boolean isRecurring;
    private String recurringPattern;
    private String notes;
}
