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
public class RevenueReportDTO {
    private PeriodDTO period;
    private BigDecimal totalRevenue;
    private Long totalOrders;
    private BigDecimal averageOrderValue;
    private List<PeriodSummaryDTO> byPeriod;
    private List<PaymentMethodSummaryDTO> byPaymentMethod;
    private List<ServiceSummaryDTO> byServiceType;
    private List<ShopSummaryDTO> byShop;
    
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
    public static class PeriodSummaryDTO {
        private String period;
        private BigDecimal revenue;
        private Long orders;
        private BigDecimal aov;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentMethodSummaryDTO {
        private String method;
        private BigDecimal amount;
        private Double percentage;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ServiceSummaryDTO {
        private String service;
        private BigDecimal amount;
        private Long orders;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ShopSummaryDTO {
        private UUID shopId;
        private String shopName;
        private BigDecimal amount;
        private Long orders;
    }
}
