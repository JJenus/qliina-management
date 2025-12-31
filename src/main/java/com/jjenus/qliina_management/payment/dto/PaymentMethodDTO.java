package com.jjenus.qliina_management.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentMethodDTO {
    private UUID id;
    private String name;
    private String type;
    private String icon;
    private Boolean isActive;
    private Boolean requiresReference;
    private Double surcharge;
    private Double minAmount;
    private Double maxAmount;
}
