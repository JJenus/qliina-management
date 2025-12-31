package com.jjenus.qliina_management.identity.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ForgotPasswordRequest {
    @NotBlank(message = "Username or email is required")
    private String username;
}
