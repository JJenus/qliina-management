package com.jjenus.qliina_management.business.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceGarmentPricingDTO {
    private UUID id;
    private UUID serviceTypeId;
    private String serviceName;
    private UUID garmentTypeId;
    private String garmentName;
    private BigDecimal price;
    private Boolean isActive;
}