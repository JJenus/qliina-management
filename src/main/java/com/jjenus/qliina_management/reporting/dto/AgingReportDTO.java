package com.jjenus.qliina_management.reporting.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgingReportDTO {
    private LocalDate asOfDate;
    private BigDecimal totalReceivables;
    private Map<String, BucketDTO> buckets;
    private List<CustomerAgingDTO> byCustomer;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BucketDTO {
        private BigDecimal amount;
        private Long count;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CustomerAgingDTO {
        private UUID customerId;
        private String customerName;
        private BigDecimal totalDue;
        private BigDecimal current;
        private BigDecimal days30;
        private BigDecimal days60;
        private BigDecimal days90;
        private BigDecimal days90Plus;
        private LocalDate oldestInvoice;
    }
}
