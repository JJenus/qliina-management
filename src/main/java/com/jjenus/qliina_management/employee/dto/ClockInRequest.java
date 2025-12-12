package com.jjenus.qliina_management.employee.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class ClockInRequest {
    @NotNull(message = "Shop ID is required")
    private UUID shopId;
    
    private String notes;
    
    private DeviceInfo deviceInfo;
    
    @Data
    public static class DeviceInfo {
        private String deviceId;
        private String location;
    }
}
