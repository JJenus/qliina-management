package com.jjenus.qliina_management.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class Verify2FARequest {
    @NotNull(message = "User ID is required")
    private UUID userId;
    
    @NotBlank(message = "2FA code is required")
    private String code;
    
    private Boolean rememberDevice = false;
}
