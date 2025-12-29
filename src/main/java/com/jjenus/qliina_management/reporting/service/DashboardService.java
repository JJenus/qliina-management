package com.jjenus.qliina_management.reporting.service;

import com.jjenus.qliina_management.employee.repository.EmployeeShiftRepository;
import com.jjenus.qliina_management.identity.repository.ShopRepository;
import com.jjenus.qliina_management.inventory.repository.ShopStockRepository;
import com.jjenus.qliina_management.payment.repository.OrderPaymentRepository;
import com.jjenus.qliina_management.order.repository.OrderRepository;
import com.jjenus.qliina_management.payment.repository.InvoiceRepository;
import com.jjenus.qliina_management.reporting.dto.DashboardSummaryDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import com.jjenus.qliina_management.inventory.model.ShopStock;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {
    
    private final OrderRepository orderRepository;
    private final OrderPaymentRepository paymentRepository;
    private final EmployeeShiftRepository shiftRepository;
    private final ShopRepository shopRepository;
    private final ShopStockRepository shopStockRepository;
    private final InvoiceRepository invoiceRepository;
    
    public DashboardSummaryDTO getDashboardSummary(UUID businessId, UUID shopId) {
        LocalDate today = LocalDate.now();
        LocalDateTime startOfToday = today.atStartOfDay();
        LocalDateTime endOfToday = today.atTime(23, 59, 59);
        
        LocalDateTime startOfYesterday = today.minusDays(1).atStartOfDay();
        LocalDateTime endOfYesterday = today.minusDays(1).atTime(23, 59, 59);
        
        LocalDateTime startOfLastWeek = today.minusWeeks(1).atStartOfDay();
        
        // Today's revenue
        BigDecimal todayRevenue = paymentRepository.sumRevenueByDateRange(
            businessId, shopId, startOfToday, endOfToday);
        if (todayRevenue == null) todayRevenue = BigDecimal.ZERO;
        
        // Yesterday's revenue for comparison
        BigDecimal yesterdayRevenue = paymentRepository.sumRevenueByDateRange(
            businessId, shopId, startOfYesterday, endOfYesterday);
        if (yesterdayRevenue == null) yesterdayRevenue = BigDecimal.ZERO;
        
        double revenueChange = calculatePercentageChange(todayRevenue, yesterdayRevenue);
        
        // Today's orders
        Long todayOrders = orderRepository.countOrdersByDateRange(
            businessId, shopId, startOfToday, endOfToday);
        if (todayOrders == null) todayOrders = 0L;
        
        Long yesterdayOrders = orderRepository.countOrdersByDateRange(
            businessId, shopId, startOfYesterday, endOfYesterday);
        if (yesterdayOrders == null) yesterdayOrders = 0L;
        
        double ordersChange = calculatePercentageChange(
            BigDecimal.valueOf(todayOrders), BigDecimal.valueOf(yesterdayOrders));
        
        // Pending orders
        Long pendingOrders = orderRepository.countPendingOrders(businessId, shopId);
        if (pendingOrders == null) pendingOrders = 0L;
        
        // Active employees
        Long activeEmployees = shiftRepository.countActiveEmployees(shopId);
        if (activeEmployees == null) activeEmployees = 0L;
        
        // Average order value
        BigDecimal aov = todayOrders > 0 ?
            todayRevenue.divide(BigDecimal.valueOf(todayOrders), 2, RoundingMode.HALF_UP) :
            BigDecimal.ZERO;
        
        // Outstanding receivables
        BigDecimal outstanding = orderRepository.sumOutstandingBalance(businessId, shopId);
        if (outstanding == null) outstanding = BigDecimal.ZERO;
        
        // Revenue chart data (last 7 days)
        List<DashboardSummaryDTO.ChartDataDTO> revenueChart = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            LocalDateTime start = date.atStartOfDay();
            LocalDateTime end = date.atTime(23, 59, 59);
            
            BigDecimal dailyRevenue = paymentRepository.sumRevenueByDateRange(
                businessId, shopId, start, end);
            if (dailyRevenue == null) dailyRevenue = BigDecimal.ZERO;
            
            revenueChart.add(DashboardSummaryDTO.ChartDataDTO.builder()
                .label(date.format(java.time.format.DateTimeFormatter.ofPattern("MM/dd")))
                .value(dailyRevenue)
                .build());
        }
        
        // Orders chart data (last 7 days)
        List<DashboardSummaryDTO.ChartDataDTO> ordersChart = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            LocalDateTime start = date.atStartOfDay();
            LocalDateTime end = date.atTime(23, 59, 59);
            
            Long dailyOrders = orderRepository.countOrdersByDateRange(
                businessId, shopId, start, end);
            if (dailyOrders == null) dailyOrders = 0L;
            
            ordersChart.add(DashboardSummaryDTO.ChartDataDTO.builder()
                .label(date.format(java.time.format.DateTimeFormatter.ofPattern("MM/dd")))
                .value(BigDecimal.valueOf(dailyOrders))
                .build());
        }
        
        // Alerts
        List<DashboardSummaryDTO.AlertDTO> alerts = new ArrayList<>();
        
        // Low stock alerts
        if (isLowStock(businessId, shopId)) {
            alerts.add(DashboardSummaryDTO.AlertDTO.builder()
                .type("INVENTORY")
                .message("Some items are running low on stock")
                .severity("WARNING")
                .link("/inventory/alerts")
                .build());
        }
        
        // Overdue invoices
        if (hasOverdueInvoices(businessId)) {
            alerts.add(DashboardSummaryDTO.AlertDTO.builder()
                .type("PAYMENT")
                .message("There are overdue invoices")
                .severity("WARNING")
                .link("/reports/aging")
                .build());
        }
        
        // Pending quality checks
        if (hasPendingQualityChecks(businessId, shopId)) {
            alerts.add(DashboardSummaryDTO.AlertDTO.builder()
                .type("QUALITY")
                .message("Orders pending quality check")
                .severity("INFO")
                .link("/quality/checks")
                .build());
        }
        
        DashboardSummaryDTO.KPIDTO kpi = DashboardSummaryDTO.KPIDTO.builder()
            .todayRevenue(todayRevenue)
            .revenueChange(revenueChange)
            .todayOrders(todayOrders.intValue())
            .ordersChange(ordersChange)
            .pendingOrders(pendingOrders.intValue())
            .activeEmployees(activeEmployees.intValue())
            .averageOrderValue(aov.doubleValue())
            .outstandingReceivables(outstanding)
            .build();
        
        return DashboardSummaryDTO.builder()
            .date(today)
            .kpi(kpi)
            .revenueChart(revenueChart)
            .ordersChart(ordersChart)
            .alerts(alerts)
            .build();
    }
    
    private double calculatePercentageChange(BigDecimal current, BigDecimal previous) {
        if (previous == null || previous.compareTo(BigDecimal.ZERO) == 0) {
            return current.compareTo(BigDecimal.ZERO) > 0 ? 100 : 0;
        }
        return current.subtract(previous)
            .divide(previous, 4, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100))
            .doubleValue();
    }
    
    private boolean isLowStock(UUID businessId, UUID shopId) {
        if (shopId == null) return false;
        long lowStockCount = shopStockRepository.countByShopIdAndStatus(
    shopId, ShopStock.StockStatus.LOW, ShopStock.StockStatus.CRITICAL);
        return lowStockCount > 0;
    }
    
    private boolean hasOverdueInvoices(UUID businessId) {
        LocalDate today = LocalDate.now();
        long overdueCount = invoiceRepository.countByStatusAndDueDateBefore("SENT", today);
        return overdueCount > 0;
    }
    
    private boolean hasPendingQualityChecks(UUID businessId, UUID shopId) {
        // In a real implementation, query quality check repository
        return false;
    }
}
