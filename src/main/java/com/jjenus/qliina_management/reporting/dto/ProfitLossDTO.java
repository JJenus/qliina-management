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
public class ProfitLossDTO {
    private PeriodDTO period;
    private RevenueDTO revenue;
    private ExpensesDTO expenses;
    private BigDecimal grossProfit;
    private Double grossMargin;
    private BigDecimal netProfit;
    private Double netMargin;
    
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
    public static class RevenueDTO {
        private BigDecimal total;
        private List<ServiceBreakdownDTO> byService;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ServiceBreakdownDTO {
        private String service;
        private BigDecimal amount;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExpensesDTO {
        private BigDecimal total;
        private List<ExpenseCategoryDTO> categories;
        private List<ExpenseDetailDTO> details;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExpenseCategoryDTO {
        private String category;
        private BigDecimal amount;
        private Double percentage;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExpenseDetailDTO {
        private LocalDate date;
        private String category;
        private String description;
        private BigDecimal amount;
        private String paidTo;
    }
}
