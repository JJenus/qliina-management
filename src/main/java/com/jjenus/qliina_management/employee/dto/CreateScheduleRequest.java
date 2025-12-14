package com.jjenus.qliina_management.employee.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Data
public class CreateScheduleRequest {
    @NotNull(message = "Shop ID is required")
    private UUID shopId;
    
    @NotNull(message = "Employee ID is required")
    private UUID employeeId;
    
    private LocalDate date;
    
    @NotNull(message = "Start time is required")
    private LocalTime startTime;
    
    @NotNull(message = "End time is required")
    private LocalTime endTime;
    
    private String role;
    
    private Boolean isRecurring;
    
    private String recurringPattern;
    
    private String notes;
}
