package com.jjenus.qliina_management.reporting.service;

import com.jjenus.qliina_management.order.model.Order;
import com.jjenus.qliina_management.order.model.OrderItem;
import com.jjenus.qliina_management.order.repository.OrderRepository;
import com.jjenus.qliina_management.reporting.dto.SalesByServiceDTO;
import com.jjenus.qliina_management.reporting.dto.SalesByServiceRequest;
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
public class SalesReportService {
    
    private final OrderRepository orderRepository;
    
    public SalesByServiceDTO generateSalesByServiceReport(UUID businessId, SalesByServiceRequest request) {
        LocalDateTime startDateTime = request.getStartDate().atStartOfDay();
        LocalDateTime endDateTime = request.getEndDate().atTime(23, 59, 59);
        
        List<Order> orders = orderRepository.findByDateRange(businessId, startDateTime, endDateTime, null)
            .getContent();
        
        Map<String, ServiceSales> serviceMap = new HashMap<>();
        Map<LocalDate, DailySales> dailyMap = new LinkedHashMap<>();
        
        // Initialize daily map
        LocalDate current = request.getStartDate();
        while (!current.isAfter(request.getEndDate())) {
            dailyMap.put(current, new DailySales());
            current = current.plusDays(1);
        }
        
        for (Order order : orders) {
            LocalDate orderDate = order.getCreatedAt().toLocalDate();
            DailySales daily = dailyMap.computeIfAbsent(orderDate, k -> new DailySales());
            
            for (OrderItem item : order.getItems()) {
                String serviceType = item.getServiceType();
                ServiceSales service = serviceMap.computeIfAbsent(serviceType, k -> new ServiceSales());
                
                service.addOrder(item.getTotal(), order.getId());
                service.addItem();
                
                daily.addOrder();
                daily.addItem();
                daily.addRevenue(item.getTotal());
            }
        }
        
        BigDecimal totalRevenue = serviceMap.values().stream()
            .map(s -> s.revenue)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        long totalOrders = serviceMap.values().stream()
            .mapToLong(s -> s.uniqueOrders.size())
            .sum();
        
        List<SalesByServiceDTO.ServiceSalesDTO> serviceSales = serviceMap.entrySet().stream()
            .map(e -> {
                ServiceSales s = e.getValue();
                double percentage = totalRevenue.compareTo(BigDecimal.ZERO) > 0 ?
                    s.revenue.divide(totalRevenue, 4, RoundingMode.HALF_UP).doubleValue() * 100 : 0;
                BigDecimal aov = s.uniqueOrders.size() > 0 ?
                    s.revenue.divide(BigDecimal.valueOf(s.uniqueOrders.size()), 2, RoundingMode.HALF_UP) :
                    BigDecimal.ZERO;
                
                return SalesByServiceDTO.ServiceSalesDTO.builder()
                    .serviceName(e.getKey())
                    .orderCount(s.uniqueOrders.size())
                    .itemCount(s.itemCount)
                    .revenue(s.revenue)
                    .percentage(percentage)
                    .averageOrderValue(aov)
                    .build();
            })
            .sorted((a, b) -> b.getRevenue().compareTo(a.getRevenue()))
            .collect(Collectors.toList());
        
        Map<String, List<SalesByServiceDTO.DailySalesDTO>> dailyBreakdown = new HashMap<>();
        for (Map.Entry<LocalDate, DailySales> entry : dailyMap.entrySet()) {
            String dateKey = entry.getKey().format(DateTimeFormatter.ISO_DATE);
            DailySales ds = entry.getValue();
            
            dailyBreakdown.computeIfAbsent("ALL", k -> new ArrayList<>())
                .add(SalesByServiceDTO.DailySalesDTO.builder()
                    .date(entry.getKey())
                    .orders(ds.orderCount)
                    .items(ds.itemCount)
                    .revenue(ds.revenue)
                    .build());
        }
        
        return SalesByServiceDTO.builder()
            .period(SalesByServiceDTO.PeriodDTO.builder()
                .start(request.getStartDate())
                .end(request.getEndDate())
                .build())
            .services(serviceSales)
            .dailyBreakdown(dailyBreakdown)
            .build();
    }
    
    private static class ServiceSales {
        BigDecimal revenue = BigDecimal.ZERO;
        Set<UUID> uniqueOrders = new HashSet<>();
        int itemCount = 0;
        
        void addOrder(BigDecimal amount, UUID orderId) {
            revenue = revenue.add(amount);
            uniqueOrders.add(orderId);
        }
        
        void addItem() {
            itemCount++;
        }
    }
    
    private static class DailySales {
        int orderCount = 0;
        int itemCount = 0;
        BigDecimal revenue = BigDecimal.ZERO;
        
        void addOrder() {
            orderCount++;
        }
        
        void addItem() {
            itemCount++;
        }
        
        void addRevenue(BigDecimal amount) {
            revenue = revenue.add(amount);
        }
    }
}
