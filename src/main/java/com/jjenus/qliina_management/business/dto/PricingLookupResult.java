package com.jjenus.qliina_management.business.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PricingLookupResult {
    private BigDecimal price;
    private String source; // "SPECIFIC" or "DEFAULT"
    private String serviceName;
    private String garmentName;
}