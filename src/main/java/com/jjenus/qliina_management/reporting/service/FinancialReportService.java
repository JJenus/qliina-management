package com.jjenus.qliina_management.reporting.service;

import com.jjenus.qliina_management.order.model.Order;
import com.jjenus.qliina_management.payment.model.OrderPayment;
import com.jjenus.qliina_management.payment.repository.OrderPaymentRepository;
import com.jjenus.qliina_management.order.repository.OrderRepository;
import com.jjenus.qliina_management.reporting.dto.DateRangeRequest;
import com.jjenus.qliina_management.reporting.dto.ProfitLossDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FinancialReportService {
    
    private final OrderRepository orderRepository;
    private final OrderPaymentRepository paymentRepository;
    
    public ProfitLossDTO generateProfitLoss(UUID businessId, DateRangeRequest request) {
        LocalDateTime startDateTime = request.getStartDate().atStartOfDay();
        LocalDateTime endDateTime = request.getEndDate().atTime(23, 59, 59);
        
        List<Order> orders = orderRepository.findByDateRange(businessId, startDateTime, endDateTime, null)
            .getContent();
        
        List<OrderPayment> payments = paymentRepository.findByFilters(
            businessId, null, null, null, null, "COMPLETED", 
            startDateTime, endDateTime, null, null).getContent();
        
        // Calculate revenue
        BigDecimal totalRevenue = payments.stream()
            .map(OrderPayment::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // Calculate revenue by service
        Map<String, BigDecimal> revenueByService = new HashMap<>();
        for (Order order : orders) {
            for (var item : order.getItems()) {
                revenueByService.merge(item.getServiceType(), item.getTotal(), BigDecimal::add);
            }
        }
        
        List<ProfitLossDTO.ServiceBreakdownDTO> serviceBreakdown = revenueByService.entrySet().stream()
            .map(e -> ProfitLossDTO.ServiceBreakdownDTO.builder()
                .service(e.getKey())
                .amount(e.getValue())
                .build())
            .collect(Collectors.toList());
        
        // Calculate expenses (simplified - in real app, would come from expense tracking module)
        List<ProfitLossDTO.ExpenseCategoryDTO> expenseCategories = calculateExpenseCategories(businessId, startDateTime, endDateTime);
        List<ProfitLossDTO.ExpenseDetailDTO> expenseDetails = calculateExpenseDetails(businessId, startDateTime, endDateTime);
        
        BigDecimal totalExpenses = expenseCategories.stream()
            .map(ProfitLossDTO.ExpenseCategoryDTO::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal grossProfit = totalRevenue.subtract(totalExpenses);
        double grossMargin = totalRevenue.compareTo(BigDecimal.ZERO) > 0 ?
            grossProfit.divide(totalRevenue, 4, RoundingMode.HALF_UP).doubleValue() * 100 : 0;
        
        // Net profit (after additional costs like taxes)
        BigDecimal netProfit = grossProfit;
        double netMargin = grossMargin;
        
        return ProfitLossDTO.builder()
            .period(ProfitLossDTO.PeriodDTO.builder()
                .start(request.getStartDate())
                .end(request.getEndDate())
                .build())
            .revenue(ProfitLossDTO.RevenueDTO.builder()
                .total(totalRevenue)
                .byService(serviceBreakdown)
                .build())
            .expenses(ProfitLossDTO.ExpensesDTO.builder()
                .total(totalExpenses)
                .categories(expenseCategories)
                .details(expenseDetails)
                .build())
            .grossProfit(grossProfit)
            .grossMargin(grossMargin)
            .netProfit(netProfit)
            .netMargin(netMargin)
            .build();
    }
    
    private List<ProfitLossDTO.ExpenseCategoryDTO> calculateExpenseCategories(
            UUID businessId, LocalDateTime startDate, LocalDateTime endDate) {
        
        // In a real implementation, this would query actual expense records
        // For now, return sample data structure
        List<ProfitLossDTO.ExpenseCategoryDTO> categories = new ArrayList<>();
        
        // Example expense categories
        categories.add(ProfitLossDTO.ExpenseCategoryDTO.builder()
            .category("SUPPLIES")
            .amount(new BigDecimal("1250.00"))
            .percentage(25.0)
            .build());
        
        categories.add(ProfitLossDTO.ExpenseCategoryDTO.builder()
            .category("UTILITIES")
            .amount(new BigDecimal("800.00"))
            .percentage(16.0)
            .build());
        
        categories.add(ProfitLossDTO.ExpenseCategoryDTO.builder()
            .category("WAGES")
            .amount(new BigDecimal("2500.00"))
            .percentage(50.0)
            .build());
        
        categories.add(ProfitLossDTO.ExpenseCategoryDTO.builder()
            .category("RENT")
            .amount(new BigDecimal("450.00"))
            .percentage(9.0)
            .build());
        
        return categories;
    }
    
    private List<ProfitLossDTO.ExpenseDetailDTO> calculateExpenseDetails(
            UUID businessId, LocalDateTime startDate, LocalDateTime endDate) {
        
        // In a real implementation, this would query actual expense records
        List<ProfitLossDTO.ExpenseDetailDTO> details = new ArrayList<>();
        
        details.add(ProfitLossDTO.ExpenseDetailDTO.builder()
            .date(startDate.toLocalDate())
            .category("SUPPLIES")
            .description("Detergent purchase")
            .amount(new BigDecimal("500.00"))
            .paidTo("SupplyCo")
            .build());
        
        details.add(ProfitLossDTO.ExpenseDetailDTO.builder()
            .date(startDate.toLocalDate().plusDays(5))
            .category("UTILITIES")
            .description("Electricity bill")
            .amount(new BigDecimal("400.00"))
            .paidTo("Power Company")
            .build());
        
        return details;
    }
}
