package com.jjenus.qliina_management.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceDTO {
    private UUID id;
    private String invoiceNumber;
    private UUID accountId;
    private String companyName;
    private PeriodDTO period;
    private LocalDate dueDate;
    private List<InvoiceItemDTO> items;
    private BigDecimal subtotal;
    private BigDecimal tax;
    private BigDecimal total;
    private String status;
    private String pdfUrl;
    private LocalDateTime sentAt;
    private LocalDateTime paidAt;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PeriodDTO {
        private LocalDate start;
        private LocalDate end;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InvoiceItemDTO {
        private UUID orderId;
        private String orderNumber;
        private LocalDateTime orderDate;
        private BigDecimal amount;
        private String status;
    }
}
