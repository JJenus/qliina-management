package com.jjenus.qliina_management.reporting.service;

import com.jjenus.qliina_management.identity.repository.ShopRepository;
import com.jjenus.qliina_management.order.model.Order;
import com.jjenus.qliina_management.payment.model.OrderPayment;
import com.jjenus.qliina_management.payment.repository.OrderPaymentRepository;
import com.jjenus.qliina_management.order.repository.OrderRepository;
import com.jjenus.qliina_management.reporting.dto.RevenueReportDTO;
import com.jjenus.qliina_management.reporting.dto.RevenueReportRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RevenueReportService {
    
    private final OrderRepository orderRepository;
    private final OrderPaymentRepository paymentRepository;
    private final ShopRepository shopRepository;
    
    public RevenueReportDTO generateRevenueReport(UUID businessId, RevenueReportRequest request) {
        LocalDateTime startDateTime = request.getStartDate().atStartOfDay();
        LocalDateTime endDateTime = request.getEndDate().atTime(23, 59, 59);
        
        List<Order> orders = orderRepository.findByDateRange(businessId, startDateTime, endDateTime, null)
            .getContent();
        
        List<OrderPayment> payments = paymentRepository.findByFilters(
            businessId, null, null, request.getShopId(), null, "COMPLETED", 
            startDateTime, endDateTime, null, null).getContent();
        
        BigDecimal totalRevenue = payments.stream()
            .map(OrderPayment::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        Long totalOrders = (long) orders.size();
        BigDecimal averageOrderValue = totalOrders > 0 ? 
            totalRevenue.divide(BigDecimal.valueOf(totalOrders), 2, RoundingMode.HALF_UP) : 
            BigDecimal.ZERO;
        
        List<RevenueReportDTO.PeriodSummaryDTO> byPeriod = generatePeriodBreakdown(
            payments, orders, request.getGroupBy(), request.getStartDate(), request.getEndDate());
        
        List<RevenueReportDTO.PaymentMethodSummaryDTO> byPaymentMethod = generatePaymentMethodBreakdown(payments);
        
        List<RevenueReportDTO.ServiceSummaryDTO> byServiceType = generateServiceBreakdown(orders);
        
        List<RevenueReportDTO.ShopSummaryDTO> byShop = generateShopBreakdown(businessId, payments, orders, request.getShopId());
        
        return RevenueReportDTO.builder()
            .period(RevenueReportDTO.PeriodDTO.builder()
                .start(request.getStartDate())
                .end(request.getEndDate())
                .build())
            .totalRevenue(totalRevenue)
            .totalOrders(totalOrders)
            .averageOrderValue(averageOrderValue)
            .byPeriod(byPeriod)
            .byPaymentMethod(byPaymentMethod)
            .byServiceType(byServiceType)
            .byShop(byShop)
            .build();
    }
    
    private List<RevenueReportDTO.PeriodSummaryDTO> generatePeriodBreakdown(
            List<OrderPayment> payments, List<Order> orders, String groupBy, 
            LocalDate startDate, LocalDate endDate) {
        
        Map<String, PeriodSummary> summaryMap = new LinkedHashMap<>();
        DateTimeFormatter formatter;
        
        switch (groupBy) {
            case "HOUR":
                formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:00");
                break;
            case "DAY":
                formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
                break;
            case "WEEK":
                formatter = DateTimeFormatter.ofPattern("yyyy-'W'ww");
                break;
            case "MONTH":
                formatter = DateTimeFormatter.ofPattern("yyyy-MM");
                break;
            default:
                formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        }
        
        // Initialize all periods
        LocalDate current = startDate;
        while (!current.isAfter(endDate)) {
            String key;
            if ("WEEK".equals(groupBy)) {
                key = current.format(DateTimeFormatter.ofPattern("yyyy-'W'ww"));
                current = current.plusWeeks(1);
            } else if ("MONTH".equals(groupBy)) {
                key = current.format(DateTimeFormatter.ofPattern("yyyy-MM"));
                current = current.plusMonths(1);
            } else {
                key = current.format(formatter);
                current = current.plusDays(1);
            }
            summaryMap.put(key, new PeriodSummary());
        }
        
        // Aggregate payments
        for (OrderPayment payment : payments) {
            String key = payment.getPaidAt().format(formatter);
            PeriodSummary summary = summaryMap.computeIfAbsent(key, k -> new PeriodSummary());
            summary.addRevenue(payment.getAmount());
        }
        
        // Count orders
        for (Order order : orders) {
            String key = order.getCreatedAt().format(formatter);
            PeriodSummary summary = summaryMap.get(key);
            if (summary != null) {
                summary.incrementOrders();
            }
        }
        
        // Convert to DTOs
        List<RevenueReportDTO.PeriodSummaryDTO> result = new ArrayList<>();
        for (Map.Entry<String, PeriodSummary> entry : summaryMap.entrySet()) {
            PeriodSummary summary = entry.getValue();
            BigDecimal aov = summary.orderCount > 0 ? 
                summary.revenue.divide(BigDecimal.valueOf(summary.orderCount), 2, RoundingMode.HALF_UP) : 
                BigDecimal.ZERO;
            
            result.add(RevenueReportDTO.PeriodSummaryDTO.builder()
                .period(entry.getKey())
                .revenue(summary.revenue)
                .orders(summary.orderCount)
                .aov(aov)
                .build());
        }
        
        return result;
    }
    
    private List<RevenueReportDTO.PaymentMethodSummaryDTO> generatePaymentMethodBreakdown(List<OrderPayment> payments) {
        Map<String, PaymentMethodSummary> summaryMap = new HashMap<>();
        
        for (OrderPayment payment : payments) {
            summaryMap.computeIfAbsent(payment.getMethod(), k -> new PaymentMethodSummary())
                .addAmount(payment.getAmount());
        }
        
        BigDecimal total = payments.stream()
            .map(OrderPayment::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        List<RevenueReportDTO.PaymentMethodSummaryDTO> result = new ArrayList<>();
        for (Map.Entry<String, PaymentMethodSummary> entry : summaryMap.entrySet()) {
            PaymentMethodSummary summary = entry.getValue();
            double percentage = total.compareTo(BigDecimal.ZERO) > 0 ? 
                summary.amount.divide(total, 4, RoundingMode.HALF_UP).doubleValue() * 100 : 0;
            
            result.add(RevenueReportDTO.PaymentMethodSummaryDTO.builder()
                .method(entry.getKey())
                .amount(summary.amount)
                .percentage(percentage)
                .build());
        }
        
        return result;
    }
    
    private List<RevenueReportDTO.ServiceSummaryDTO> generateServiceBreakdown(List<Order> orders) {
        Map<String, ServiceSummary> summaryMap = new HashMap<>();
        
        for (Order order : orders) {
            for (var item : order.getItems()) {
                summaryMap.computeIfAbsent(item.getServiceType(), k -> new ServiceSummary())
                    .addOrder(item.getTotal(), order.getId());
            }
        }
        
        List<RevenueReportDTO.ServiceSummaryDTO> result = new ArrayList<>();
        for (Map.Entry<String, ServiceSummary> entry : summaryMap.entrySet()) {
            ServiceSummary summary = entry.getValue();
            result.add(RevenueReportDTO.ServiceSummaryDTO.builder()
                .service(entry.getKey())
                .amount(summary.revenue)
                .orders(summary.uniqueOrders.stream().count())
                .build());
        }
        
        return result;
    }
    
    private List<RevenueReportDTO.ShopSummaryDTO> generateShopBreakdown(
            UUID businessId, List<OrderPayment> payments, List<Order> orders, UUID filterShopId) {
        
        if (filterShopId != null) {
            // Single shop report
            String shopName = shopRepository.findById(filterShopId)
                .map(s -> s.getName()).orElse("");
            
            BigDecimal shopRevenue = payments.stream()
                .filter(p -> filterShopId.equals(p.getShopId()))
                .map(OrderPayment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            Long shopOrders = orders.stream()
                .filter(o -> filterShopId.equals(o.getShopId()))
                .count();
            
            return Collections.singletonList(RevenueReportDTO.ShopSummaryDTO.builder()
                .shopId(filterShopId)
                .shopName(shopName)
                .amount(shopRevenue)
                .orders(shopOrders)
                .build());
        }
        
        // All shops
        Map<UUID, ShopSummary> summaryMap = new HashMap<>();
        
        for (OrderPayment payment : payments) {
            summaryMap.computeIfAbsent(payment.getShopId(), k -> new ShopSummary())
                .addRevenue(payment.getAmount());
        }
        
        for (Order order : orders) {
            summaryMap.computeIfAbsent(order.getShopId(), k -> new ShopSummary())
                .incrementOrders();
        }
        
        List<RevenueReportDTO.ShopSummaryDTO> result = new ArrayList<>();
        for (Map.Entry<UUID, ShopSummary> entry : summaryMap.entrySet()) {
            UUID shopId = entry.getKey();
            ShopSummary summary = entry.getValue();
            String shopName = shopRepository.findById(shopId).map(s -> s.getName()).orElse("");
            
            result.add(RevenueReportDTO.ShopSummaryDTO.builder()
                .shopId(shopId)
                .shopName(shopName)
                .amount(summary.revenue)
                .orders(summary.orderCount)
                .build());
        }
        
        return result;
    }
    
    // Helper classes
    private static class PeriodSummary {
        BigDecimal revenue = BigDecimal.ZERO;
        long orderCount = 0;
        
        void addRevenue(BigDecimal amount) {
            revenue = revenue.add(amount);
        }
        
        void incrementOrders() {
            orderCount++;
        }
    }
    
    private static class PaymentMethodSummary {
        BigDecimal amount = BigDecimal.ZERO;
        
        void addAmount(BigDecimal amt) {
            amount = amount.add(amt);
        }
    }
    
    private static class ServiceSummary {
        BigDecimal revenue = BigDecimal.ZERO;
        Set<UUID> uniqueOrders = new HashSet<>();
        
        void addOrder(BigDecimal amt, UUID orderId) {
            revenue = revenue.add(amt);
            uniqueOrders.add(orderId);
        }
    }
    
    private static class ShopSummary {
        BigDecimal revenue = BigDecimal.ZERO;
        long orderCount = 0;
        
        void addRevenue(BigDecimal amt) {
            revenue = revenue.add(amt);
        }
        
        void incrementOrders() {
            orderCount++;
        }
    }
}
