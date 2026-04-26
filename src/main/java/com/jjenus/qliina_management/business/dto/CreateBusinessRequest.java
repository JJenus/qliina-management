package com.jjenus.qliina_management.business.dto;

import com.jjenus.qliina_management.common.AddressDTO;
import jakarta.validation.constraints.*;
import lombok.Data;

/**
 * Payload for POST /api/v1/auth/register-business.
 *
 * Contains both business-level and first-user (owner) fields so a Business,
 * its initial Shop, and the BUSINESS_OWNER user can be created atomically.
 */
@Data
public class CreateBusinessRequest {

    // -- Business -------------------------------------------------------------
    @NotBlank(message = "Business name is required")
    @Size(max = 120, message = "Business name must not exceed 120 characters")
    private String businessName;

    /**
     * Optional URL-safe slug. Auto-generated from businessName + hex suffix
     * if omitted.
     */
    private String slug;

    @Email(message = "Invalid business email")
    private String businessEmail;

    private String businessPhone;

    private AddressDTO businessAddress;

    // -- Initial shop ---------------------------------------------------------
    @NotBlank(message = "Shop name is required")
    @Size(max = 120, message = "Shop name must not exceed 120 characters")
    private String shopName;
    
    private String shopCode;

    // -- Owner / first user ---------------------------------------------------
    @NotBlank(message = "First name is required")
    private String firstName;

    @NotBlank(message = "Last name is required")
    private String lastName;

    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 50, message = "Username must be 3-50 characters")
    private String username;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Phone is required")
    private String phone;

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;

    @NotBlank(message = "Please confirm your password")
    private String confirmPassword;
}
