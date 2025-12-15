package com.jjenus.qliina_management.employee.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
public class SetTargetsRequest {
    @NotNull(message = "Employee ID is required")
    private UUID employeeId;
    
    @NotNull(message = "Targets are required")
    private List<TargetDTO> targets;
    
    @Data
    public static class TargetDTO {
        @NotNull(message = "Date is required")
        private LocalDate date;
        
        @NotNull(message = "Metric is required")
        private String metric;
        
        @NotNull(message = "Target value is required")
        private Integer target;
    }
}
