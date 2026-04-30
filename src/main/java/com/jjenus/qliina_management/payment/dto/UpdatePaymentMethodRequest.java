// ./src/main/java/com/jjenus/qliina_management/payment/dto/UpdatePaymentMethodRequest.java
package com.jjenus.qliina_management.payment.dto;

import lombok.Data;

@Data
public class UpdatePaymentMethodRequest {
    private String name;
    private String icon;
    private Double surcharge;
    private Double minAmount;
    private Double maxAmount;
}