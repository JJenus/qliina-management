package com.jjenus.qliina_management.employee.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ResumeRequest {
    @NotNull(message = "Password is required")
    private String password;
}
