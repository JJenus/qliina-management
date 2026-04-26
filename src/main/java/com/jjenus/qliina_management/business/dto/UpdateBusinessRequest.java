package com.jjenus.qliina_management.business.dto;

import com.jjenus.qliina_management.business.model.Business;
import com.jjenus.qliina_management.common.AddressDTO;
import jakarta.validation.constraints.*;
import lombok.Data;

/**
 * Partial-update payload for PUT /api/v1/businesses/{businessId}.
 * All fields are optional; only non-null values are applied.
 */
@Data
public class UpdateBusinessRequest {
    @Size(max = 120) private String       name;
    @Email           private String       email;
    private String phone;
    private String       logoUrl;
    private AddressDTO   address;
    /** Plan changes may only be applied by a Superadmin. */
    private Business.Plan plan;
}
