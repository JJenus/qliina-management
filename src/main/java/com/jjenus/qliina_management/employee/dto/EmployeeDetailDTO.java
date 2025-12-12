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
public class EmployeeDetailDTO {
    private UUID id;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private String role;
    private UUID shopId;
    private String shopName;
    private LocalDate hireDate;
    private Double hourlyRate;
    private String employmentStatus;
    private LocalDateTime lastClockIn;
    private Boolean isClockedIn;
}