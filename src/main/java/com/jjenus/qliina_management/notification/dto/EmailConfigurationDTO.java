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
public class EmailConfigurationDTO {
    private UUID businessId;
    private String host;
    private Integer port;
    private String username; 
    private String password;  
    private String fromAddress;
    private String fromName;
    private Boolean useTls;
    private Boolean useSsl;
    private Boolean isConfigured;
}
