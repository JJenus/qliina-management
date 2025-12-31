package com.jjenus.qliina_management.audit.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ConsentRequest {
    @NotNull(message = "Consent type is required")
    private String consentType;
    
    @NotNull(message = "Granted flag is required")
    private Boolean granted;
    
    private String consentVersion;
    
    private String notes;
}
