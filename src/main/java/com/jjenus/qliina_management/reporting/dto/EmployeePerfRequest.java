package com.jjenus.qliina_management.reporting.dto;

import lombok.Data;
import java.time.LocalDate;
import java.util.UUID;

@Data
public class EmployeePerfRequest {
    private LocalDate startDate;
    private LocalDate endDate;
    private UUID shopId;
    private UUID employeeId;
    private String role;
}
