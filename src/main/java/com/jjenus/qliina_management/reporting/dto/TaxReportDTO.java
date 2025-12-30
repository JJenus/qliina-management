package com.jjenus.qliina_management.reporting.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaxReportDTO {
    private PeriodDTO period;
    private BigDecimal totalSales;
    private BigDecimal taxableSales;
    private BigDecimal taxRate;
    private BigDecimal taxCollected;
    private List<TaxDetailDTO> details;
    
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
    public static class TaxDetailDTO {
        private LocalDate date;
        private String invoiceNumber;
        private String customerName;
        private BigDecimal amount;
        private BigDecimal tax;
    }
}
