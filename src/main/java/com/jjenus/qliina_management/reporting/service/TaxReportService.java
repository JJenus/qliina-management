package com.jjenus.qliina_management.reporting.service;

import com.jjenus.qliina_management.identity.model.BusinessConfig;
import com.jjenus.qliina_management.identity.repository.BusinessConfigRepository;
import com.jjenus.qliina_management.order.model.Order;
import com.jjenus.qliina_management.order.repository.OrderRepository;
import com.jjenus.qliina_management.reporting.dto.TaxReportDTO;
import com.jjenus.qliina_management.reporting.dto.TaxReportRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaxReportService {
    
    private final OrderRepository orderRepository;
    private final BusinessConfigRepository configRepository;
    
    public TaxReportDTO generateTaxReport(UUID businessId, TaxReportRequest request) {
        LocalDateTime startDateTime = request.getStartDate().atStartOfDay();
        LocalDateTime endDateTime = request.getEndDate().atTime(23, 59, 59);
        
        List<Order> orders = orderRepository.findByDateRange(businessId, startDateTime, endDateTime, null)
            .getContent();
        
        BusinessConfig config = configRepository.findByBusinessId(businessId)
            .orElseThrow(() -> new RuntimeException("Business configuration not found"));
        
        BigDecimal taxRate = config.getTaxRate() != null ? 
            config.getTaxRate() : BigDecimal.ZERO;
        
        BigDecimal totalSales = orders.stream()
            .map(Order::getTotalAmount)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // Calculate taxable sales (excluding tax-exempt items)
        BigDecimal taxableSales = calculateTaxableSales(orders);
        BigDecimal taxCollected = taxableSales.multiply(taxRate).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        
        List<TaxReportDTO.TaxDetailDTO> details = new ArrayList<>();
        for (Order order : orders) {
            if (isTaxable(order)) {
                details.add(TaxReportDTO.TaxDetailDTO.builder()
                    .date(order.getCreatedAt().toLocalDate())
                    .invoiceNumber(order.getOrderNumber())
                    .customerName(getCustomerName(order.getCustomerId()))
                    .amount(order.getTotalAmount())
                    .tax(calculateOrderTax(order, taxRate))
                    .build());
            }
        }
        
        return TaxReportDTO.builder()
            .period(TaxReportDTO.PeriodDTO.builder()
                .start(request.getStartDate())
                .end(request.getEndDate())
                .build())
            .totalSales(totalSales)
            .taxableSales(taxableSales)
            .taxRate(taxRate)
            .taxCollected(taxCollected)
            .details(details)
            .build();
    }
    
    private BigDecimal calculateTaxableSales(List<Order> orders) {
        return orders.stream()
            .filter(this::isTaxable)
            .map(Order::getTotalAmount)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    private boolean isTaxable(Order order) {
        // In a real implementation, check if order contains taxable items
        // based on service types or business rules
        return true;
    }
    
    private BigDecimal calculateOrderTax(Order order, BigDecimal taxRate) {
        if (!isTaxable(order) || order.getTotalAmount() == null) {
            return BigDecimal.ZERO;
        }
        return order.getTotalAmount()
            .multiply(taxRate)
            .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
    }
    
    private String getCustomerName(UUID customerId) {
        // In a real implementation, fetch from customer repository
        return "Customer";
    }
}
