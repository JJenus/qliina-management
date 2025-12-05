package com.jjenus.qliina_management.order.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyOrdersSummaryDTO {
    private LocalDate date;
    private UUID shopId;
    private Integer totalOrders;
    private Integer completedOrders;
    private Integer pendingOrders;
    private BigDecimal totalRevenue;
    private BigDecimal averageOrderValue;
    private List<ServiceSummaryDTO> byServiceType;
    private List<HourlyDTO> byHour;
    private Integer peakHour;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ServiceSummaryDTO {
        private String serviceType;
        private Integer count;
        private BigDecimal revenue;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HourlyDTO {
        private Integer hour;
        private Integer count;
    }
}
