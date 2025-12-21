package com.jjenus.qliina_management.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SMSConfigurationDTO {
    private UUID businessId;
    private String provider;
    private String fromNumber;
    private Boolean isConfigured;
    private String accountSid; 
    private String authToken;
}
