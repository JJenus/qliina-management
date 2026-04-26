package com.jjenus.qliina_management.business.dto;

import com.jjenus.qliina_management.business.model.Business;
import com.jjenus.qliina_management.common.AddressDTO;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

/** Read-only projection of a Business returned by API responses. */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class BusinessDTO {
    private UUID            id;
    private String          name;
    private String          slug;
    private Business.Status status;
    private Business.Plan   plan;
    private String          email;
    private String          phone;
    private String          logoUrl;
    private AddressDTO      address;
    private LocalDateTime   trialEndsAt;
    private LocalDateTime   createdAt;
    private LocalDateTime   updatedAt;
    /** Number of active shops — populated by BusinessService.toDTO(). */
    private long            activeShopCount;
}
