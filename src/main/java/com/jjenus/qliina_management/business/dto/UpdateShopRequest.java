package com.jjenus.qliina_management.business.dto;

import com.jjenus.qliina_management.common.AddressDTO;
import com.jjenus.qliina_management.identity.model.OperatingHour;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.List;

/**
 * Partial-update payload for PUT /api/v1/{businessId}/shops/{shopId}.
 * All fields optional; only non-null values are applied.
 * The shop code is immutable after creation and is absent from this DTO.
 */
@Data
public class UpdateShopRequest {
    @Size(max = 120) private String              name;
    private AddressDTO          address;
    private String              phone;
    private String              email;
    private String              timezone;
    private List<OperatingHour> operatingHours;
}
