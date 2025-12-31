package com.jjenus.qliina_management.identity.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class Disable2FARequest {
    @NotNull(message = "User ID is required")
    private UUID userId;
    
    private String code;
}
