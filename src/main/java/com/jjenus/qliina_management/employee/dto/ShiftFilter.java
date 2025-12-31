package com.jjenus.qliina_management.employee.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
public class ShiftFilter {
    private UUID shopId;
    private UUID employeeId;
    private LocalDate date;
    private String status;
}
