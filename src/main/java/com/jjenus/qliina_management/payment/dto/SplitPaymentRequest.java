package com.jjenus.qliina_management.payment.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class SplitPaymentRequest {
    @NotNull(message = "Payments are required")
    private List<PaymentSplit> payments;
    
    @Data
    public static class PaymentSplit {
        @NotNull(message = "Amount is required")
        private Double amount;
        
        @NotNull(message = "Payment method is required")
        private String method;
        
        private String reference;
        
        private Double tip;
    }
}
