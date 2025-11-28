package com.jjenus.qliina_management.customer.dto;

import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class UpdateCustomerRequest {
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
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
