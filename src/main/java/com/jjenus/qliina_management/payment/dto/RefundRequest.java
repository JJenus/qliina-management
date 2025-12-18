package com.jjenus.qliina_management.payment.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RefundRequest {
    @NotNull(message = "Amount is required")
    private Double amount;
    
    @NotNull(message = "Reason is required")
    private String reason;
    
    private String reasonCode;
    
    private String method;
    
    private String notes;
    
    private String approvalCode;
}
