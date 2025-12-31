package com.jjenus.qliina_management.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RegisterDeviceRequest {
    @NotBlank(message = "Device ID is required")
    private String deviceId;
    
    @NotNull(message = "Device type is required")
    private String deviceType;
    
    private String pushToken;
    
    private String appVersion;
    
    private String osVersion;
    
    private String model;
}
