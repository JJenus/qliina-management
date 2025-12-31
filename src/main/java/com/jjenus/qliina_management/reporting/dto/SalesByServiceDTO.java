package com.jjenus.qliina_management.reporting.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SalesByServiceDTO {
    private PeriodDTO period;
    private List<ServiceSalesDTO> services;
    private Map<String, List<DailySalesDTO>> dailyBreakdown;
    
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
    public static class ServiceSalesDTO {
        private String serviceName;
        private Integer orderCount;
        private Integer itemCount;
        private BigDecimal revenue;
        private Double percentage;
        private BigDecimal averageOrderValue;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailySalesDTO {
        private LocalDate date;
        private Integer orders;
        private Integer items;
        private BigDecimal revenue;
    }
}
