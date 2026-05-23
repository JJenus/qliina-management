package com.jjenus.qliina_management.reporting.service;

import com.jjenus.qliina_management.expense.model.ExpenseCategory;
import com.jjenus.qliina_management.expense.repository.ExpenseRepository;
import com.jjenus.qliina_management.order.model.Order;
import com.jjenus.qliina_management.payment.model.OrderPayment;
import com.jjenus.qliina_management.payment.repository.OrderPaymentRepository;
import com.jjenus.qliina_management.order.repository.OrderRepository;
import com.jjenus.qliina_management.reporting.dto.DateRangeRequest;
import com.jjenus.qliina_management.reporting.dto.ProfitLossDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import com.jjenus.qliina_management.payment.dto.PaymentFilter;
import com.jjenus.qliina_management.payment.repository.PaymentSpecifications;
import org.springframework.data.jpa.domain.Specification;

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
    private final ExpenseRepository expenseRepository;

    public ProfitLossDTO generateProfitLoss(UUID businessId, DateRangeRequest request) {
        LocalDateTime startDateTime = request.getStartDate().atStartOfDay();
        LocalDateTime endDateTime   = request.getEndDate().atTime(23, 59, 59);
        LocalDate     startDate     = request.getStartDate();
        LocalDate     endDate       = request.getEndDate();

        // ── Revenue: sum completed payments in the period ──────────────────
        PaymentFilter paymentFilter = new PaymentFilter();
        paymentFilter.setStatus("COMPLETED");
        paymentFilter.setFromDate(startDateTime);
        paymentFilter.setToDate(endDateTime);

        Specification<OrderPayment> paymentSpec = PaymentSpecifications.withFilter(businessId, paymentFilter);
        List<OrderPayment> payments = paymentRepository.findAll(paymentSpec);

        BigDecimal totalRevenue = payments.stream()
            .map(OrderPayment::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        // ── Revenue by service type (from order items, not payments) ────────
        List<Order> orders = orderRepository
            .findByDateRange(businessId, startDateTime, endDateTime, null)
            .getContent();

        Map<String, BigDecimal> revenueByService = new LinkedHashMap<>();
        for (Order order : orders) {
            for (var item : order.getItems()) {
                String name = item.getServiceType() != null ? item.getServiceType() : "Other";
                revenueByService.merge(name, item.getTotal(), BigDecimal::add);
            }
        }

        List<ProfitLossDTO.ServiceBreakdownDTO> serviceBreakdown = revenueByService.entrySet().stream()
            .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
            .map(e -> ProfitLossDTO.ServiceBreakdownDTO.builder()
                .service(e.getKey())
                .amount(e.getValue())
                .build())
            .collect(Collectors.toList());

        // ── Expenses: query real expense records ────────────────────────────
        List<Object[]> categoryTotals = expenseRepository.sumByCategory(businessId, startDate, endDate);
        BigDecimal totalExpenses = expenseRepository.sumTotalByPeriod(businessId, startDate, endDate);
        if (totalExpenses == null) totalExpenses = BigDecimal.ZERO;

        final BigDecimal expensesTotal = totalExpenses;
        List<ProfitLossDTO.ExpenseCategoryDTO> expenseCategories = categoryTotals.stream()
            .map(row -> {
                ExpenseCategory cat = (ExpenseCategory) row[0];
                BigDecimal amt = (BigDecimal) row[1];
                double pct = expensesTotal.compareTo(BigDecimal.ZERO) > 0
                    ? amt.divide(expensesTotal, 4, RoundingMode.HALF_UP).doubleValue() * 100
                    : 0.0;
                return ProfitLossDTO.ExpenseCategoryDTO.builder()
                    .category(cat.name())
                    .amount(amt)
                    .percentage(Math.round(pct * 10.0) / 10.0)
                    .build();
            })
            .collect(Collectors.toList());

        // ── Expense details: last 50 records ────────────────────────────────
        List<ProfitLossDTO.ExpenseDetailDTO> expenseDetails = expenseRepository
            .findByBusinessIdAndExpenseDateBetweenOrderByExpenseDateDesc(businessId, startDate, endDate)
            .stream()
            .limit(50)
            .map(e -> ProfitLossDTO.ExpenseDetailDTO.builder()
                .date(e.getExpenseDate())
                .category(e.getCategory().name())
                .description(e.getDescription())
                .amount(e.getAmount())
                .paidTo(e.getPaidTo())
                .build())
            .collect(Collectors.toList());

        // ── Profit calculations ─────────────────────────────────────────────
        BigDecimal grossProfit = totalRevenue.subtract(totalExpenses);
        double grossMargin = totalRevenue.compareTo(BigDecimal.ZERO) > 0
            ? grossProfit.divide(totalRevenue, 4, RoundingMode.HALF_UP).doubleValue() * 100 : 0;

        // Net profit = gross profit (no further deductions modelled yet)
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
}
