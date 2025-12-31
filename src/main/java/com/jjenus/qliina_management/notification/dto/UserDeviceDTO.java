package com.jjenus.qliina_management.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDeviceDTO {
    private UUID id;
    private UUID userId;
    private String deviceId;
    private String deviceType;
    private String pushToken;
    private Boolean isActive;
    private LocalDateTime lastUsedAt;
    private String appVersion;
    private String osVersion;
    private String model;
}
