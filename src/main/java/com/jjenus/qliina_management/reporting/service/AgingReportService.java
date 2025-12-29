package com.jjenus.qliina_management.reporting.service;

import com.jjenus.qliina_management.customer.model.Customer;
import com.jjenus.qliina_management.customer.repository.CustomerRepository;
import com.jjenus.qliina_management.order.model.Order;
import com.jjenus.qliina_management.order.repository.OrderRepository;
import com.jjenus.qliina_management.reporting.dto.AgingReportDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgingReportService {
    
    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    
    public AgingReportDTO generateAgingReport(UUID businessId) {
        LocalDate asOfDate = LocalDate.now();
        List<Order> unpaidOrders = orderRepository.findUnpaidOrders(businessId);
        
        BigDecimal totalReceivables = unpaidOrders.stream()
            .map(Order::getBalanceDue)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        Map<String, AgingReportDTO.BucketDTO> buckets = new LinkedHashMap<>();
        buckets.put("current", new AgingReportDTO.BucketDTO(BigDecimal.ZERO, 0L));
        buckets.put("1-30_days", new AgingReportDTO.BucketDTO(BigDecimal.ZERO, 0L));
        buckets.put("31-60_days", new AgingReportDTO.BucketDTO(BigDecimal.ZERO, 0L));
        buckets.put("61-90_days", new AgingReportDTO.BucketDTO(BigDecimal.ZERO, 0L));
        buckets.put("90+_days", new AgingReportDTO.BucketDTO(BigDecimal.ZERO, 0L));
        
        Map<UUID, CustomerAging> customerMap = new HashMap<>();
        
        for (Order order : unpaidOrders) {
            if (order.getBalanceDue() == null || order.getBalanceDue().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            
            long daysOverdue = ChronoUnit.DAYS.between(order.getCreatedAt().toLocalDate(), asOfDate);
            String bucketKey = getBucketKey(daysOverdue);
            
            AgingReportDTO.BucketDTO bucket = buckets.get(bucketKey);
            bucket.setAmount(bucket.getAmount().add(order.getBalanceDue()));
            bucket.setCount(bucket.getCount() + 1);
            
            CustomerAging aging = customerMap.computeIfAbsent(order.getCustomerId(), 
                k -> new CustomerAging(order.getCustomerId()));
            aging.addOrder(order, daysOverdue);
        }
        
        List<AgingReportDTO.CustomerAgingDTO> customerAgingList = customerMap.values().stream()
            .map(this::mapToCustomerAgingDTO)
            .sorted((a, b) -> b.getTotalDue().compareTo(a.getTotalDue()))
            .collect(Collectors.toList());
        
        return AgingReportDTO.builder()
            .asOfDate(asOfDate)
            .totalReceivables(totalReceivables)
            .buckets(buckets)
            .byCustomer(customerAgingList)
            .build();
    }
    
    private String getBucketKey(long days) {
        if (days <= 0) return "current";
        if (days <= 30) return "1-30_days";
        if (days <= 60) return "31-60_days";
        if (days <= 90) return "61-90_days";
        return "90+_days";
    }
    
    private AgingReportDTO.CustomerAgingDTO mapToCustomerAgingDTO(CustomerAging aging) {
        Customer customer = customerRepository.findById(aging.customerId).orElse(null);
        String customerName = customer != null ? 
            customer.getFirstName() + " " + customer.getLastName() : "Unknown";
        
        return AgingReportDTO.CustomerAgingDTO.builder()
            .customerId(aging.customerId)
            .customerName(customerName)
            .totalDue(aging.totalDue)
            .current(aging.current)
            .days30(aging.days30)
            .days60(aging.days60)
            .days90(aging.days90)
            .days90Plus(aging.days90Plus)
            .oldestInvoice(aging.oldestInvoice)
            .build();
    }
    
    private static class CustomerAging {
        UUID customerId;
        BigDecimal totalDue = BigDecimal.ZERO;
        BigDecimal current = BigDecimal.ZERO;
        BigDecimal days30 = BigDecimal.ZERO;
        BigDecimal days60 = BigDecimal.ZERO;
        BigDecimal days90 = BigDecimal.ZERO;
        BigDecimal days90Plus = BigDecimal.ZERO;
        LocalDate oldestInvoice = null;
        
        CustomerAging(UUID customerId) {
            this.customerId = customerId;
        }
        
        void addOrder(Order order, long daysOverdue) {
            BigDecimal balance = order.getBalanceDue();
            totalDue = totalDue.add(balance);
            
            if (daysOverdue <= 0) {
                current = current.add(balance);
            } else if (daysOverdue <= 30) {
                days30 = days30.add(balance);
            } else if (daysOverdue <= 60) {
                days60 = days60.add(balance);
            } else if (daysOverdue <= 90) {
                days90 = days90.add(balance);
            } else {
                days90Plus = days90Plus.add(balance);
            }
            
            LocalDate orderDate = order.getCreatedAt().toLocalDate();
            if (oldestInvoice == null || orderDate.isBefore(oldestInvoice)) {
                oldestInvoice = orderDate;
            }
        }
    }
}
