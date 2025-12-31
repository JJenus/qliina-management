package com.jjenus.qliina_management.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.UUID;

@Data
public class UpdateUserRequest {
    @Email(message = "Invalid email format")
    private String email;
    
    @Pattern(regexp = "^\\+?[1-9]\\d{1,14}$", message = "Invalid phone number format")
    private String phone;
    
    private String firstName;
    
    private String lastName;
    
    private Boolean enabled;
    
    private UUID primaryShopId;
}
