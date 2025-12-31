package com.jjenus.qliina_management.identity.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class GrantPermissionRequest {
    private UUID permissionId;
    private UUID shopId;
    private LocalDateTime expiresAt;
}
