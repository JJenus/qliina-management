package com.jjenus.qliina_management.identity.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {
    @NotBlank(message = "Username is required")
    private String username;
    
    @NotBlank(message = "Password is required")
    private String password;
    
    private Boolean rememberMe = false;
    
    private DeviceInfo deviceInfo;
    
    @Data
    public static class DeviceInfo {
        private String deviceId;
        private String deviceType; // MOBILE, TABLET, DESKTOP
        private String pushToken;
    }
}
