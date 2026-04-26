package com.jjenus.qliina_management.business.dto;

import com.jjenus.qliina_management.common.AddressDTO;
import com.jjenus.qliina_management.identity.model.OperatingHour;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Read-only projection of a Shop returned by API responses. */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ShopDTO {
    private UUID                id;
    private UUID                businessId;
    private String              name;
    private String              code;
    private AddressDTO          address;
    private String              phone;
    private String              email;
    private String              timezone;
    private Boolean             active;
    private List<OperatingHour> operatingHours;
    private LocalDateTime       createdAt;
    private LocalDateTime       updatedAt;
}
