package com.jjenus.qliina_management.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** Result of polling a provider for the final status of a pending charge. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentVerifyDTO {
    private UUID paymentId;
    private Boolean paid;
    private String status;
    private String provider;
    private String providerReference;
}