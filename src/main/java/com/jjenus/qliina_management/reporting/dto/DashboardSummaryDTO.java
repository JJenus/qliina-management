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
public class DashboardSummaryDTO {
    private LocalDate date;
    private KPIDTO kpi;
    private List<ChartDataDTO> revenueChart;
    private List<ChartDataDTO> ordersChart;
    private Map<String, Object> topPerformers;
    private List<AlertDTO> alerts;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KPIDTO {
        private BigDecimal todayRevenue;
        private Double revenueChange;
        private Integer todayOrders;
        private Double ordersChange;
        private Integer pendingOrders;
        private Integer activeEmployees;
        private Double averageOrderValue;
        private BigDecimal outstandingReceivables;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChartDataDTO {
        private String label;
        private BigDecimal value;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AlertDTO {
        private String type;
        private String message;
        private String severity;
        private String link;
    }
}
