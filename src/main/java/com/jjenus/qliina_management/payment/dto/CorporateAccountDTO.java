package com.jjenus.qliina_management.payment.dto;

import com.jjenus.qliina_management.common.AddressDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CorporateAccountDTO {
    private UUID id;
    private UUID customerId;
    private String companyName;
    private String taxId;
    private BigDecimal creditLimit;
    private BigDecimal currentBalance;
    private BigDecimal availableCredit;
    private String paymentTerms;
    private String billingCycle;
    private AddressDTO billingAddress;
    private List<ContactDTO> contacts;
    private String status;
    private LocalDateTime lastInvoiceDate;
    private LocalDateTime lastPaymentDate;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ContactDTO {
        private String name;
        private String email;
        private String phone;
        private String role;
    }
}
