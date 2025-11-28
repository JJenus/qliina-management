package com.jjenus.qliina_management.customer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class CreateCustomerRequest {
    @NotBlank(message = "First name is required")
    private String firstName;
    
    @NotBlank(message = "Last name is required")
    private String lastName;
    
    @NotBlank(message = "Phone is required")
    @Pattern(regexp = "^\\+?[1-9]\\d{1,14}$", message = "Invalid phone number format")
    private String phone;
    
    private String email;
    
    private List<AddressDTO> addresses;
    
    private PreferencesDTO preferences;
    
    private String notes;
    
    private List<String> tags;
    
    @Data
    public static class AddressDTO {
        private String type;
        private String addressLine1;
        private String addressLine2;
        private String city;
        private String state;
        private String postalCode;
        private String country;
        private Boolean isDefault;
        private String instructions;
    }
    
    @Data
    public static class PreferencesDTO {
        private Boolean notifyViaSms;
        private Boolean notifyViaEmail;
        private String preferredPaymentMethod;
        private UUID preferredShopId;
    }
}
