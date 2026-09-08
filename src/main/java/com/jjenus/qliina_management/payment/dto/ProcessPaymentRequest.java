package com.jjenus.qliina_management.payment.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ProcessPaymentRequest {
    @NotNull(message = "Amount is required")
    private Double amount;
    
    @NotNull(message = "Payment method is required")
    private String method;
    
    private String reference;
    
    private Double tip;
    
    private Double cashReceived;

    /**
     * Optional for CARD/TRANSFER. Supplied → charge through that online provider
     * (must be enabled + configured). Omitted → the payment is manually recorded
     * (external POS / direct bank transfer) and settles immediately.
     */
    private String provider;
    
    private CardDetails cardDetails;
    
    private WalletDetails walletDetails;
    
    private String notes;
    
    @Data
    public static class CardDetails {
        private String token;
        private String last4;
        private String brand;
    }
    
    @Data
    public static class WalletDetails {
        private String provider;
        private String transactionId;
    }
}
