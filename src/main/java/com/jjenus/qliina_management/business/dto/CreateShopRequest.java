package com.jjenus.qliina_management.business.dto;

import com.jjenus.qliina_management.common.AddressDTO;
import com.jjenus.qliina_management.identity.model.OperatingHour;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.util.List;

/** Payload for POST /api/v1/{businessId}/shops. */
@Data
public class CreateShopRequest {

    @NotBlank(message = "Shop name is required")
    @Size(max = 120, message = "Shop name must not exceed 120 characters")
    private String name;

    @NotBlank(message = "Shop code is required")
    @Pattern(regexp = "^[A-Z0-9_-]{2,20}$",
             message = "Shop code must be 2-20 uppercase letters, digits, hyphens, or underscores")
    private String code;

    private AddressDTO address;
    private String phone;
    private String email;
    /** IANA timezone, e.g. "America/New_York". Defaults to business timezone if omitted. */
    private String timezone;
    private List<OperatingHour> operatingHours;
}
