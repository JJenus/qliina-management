package com.jjenus.qliina_management.payment.dto;

import com.jjenus.qliina_management.common.AddressDTO;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class CreateCorporateAccountRequest {
    @NotNull(message = "Customer ID is required")
    private UUID customerId;
    
    @NotBlank(message = "Company name is required")
    private String companyName;
    
    private String taxId;
    
    @NotNull(message = "Credit limit is required")
    private Double creditLimit;
    
    private String paymentTerms;
    
    private String billingCycle;
    
    private AddressDTO billingAddress;
    
    private List<ContactDTO> contacts;
    
    @Data
    public static class ContactDTO {
        private String name;
        private String email;
        private String phone;
        private String role;
    }
}
