// ./src/main/java/com/jjenus/qliina_management/payment/dto/CreatePaymentMethodRequest.java
package com.jjenus.qliina_management.payment.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreatePaymentMethodRequest {
    @NotBlank(message = "Payment method name is required")
    private String name;
    
    @NotBlank(message = "Payment method type is required")
    private String type;
    
    private String icon = "i-heroicons-credit-card";
    
    private Boolean requiresReference = false;
    
    private Double surcharge;
    
    private Double minAmount;
    
    private Double maxAmount;
}