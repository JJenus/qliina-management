package com.jjenus.qliina_management.identity.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class CreateRoleRequest {
    @NotBlank(message = "Role name is required")
    private String name;
    
    private String description;
    
    private List<UUID> permissions;
}
