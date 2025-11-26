package com.jjenus.qliina_management.audit.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateDataSubjectRequest {
    private UUID customerId;
    
    @NotBlank(message = "Customer name is required")
    private String customerName;
    
    @Email(message = "Invalid email format")
    @NotBlank(message = "Customer email is required")
    private String customerEmail;
    
    @NotNull(message = "Request type is required")
    private String requestType;
    
    private String requestDetails;
    
    private String verificationMethod;
}
