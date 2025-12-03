package com.jjenus.qliina_management.order.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.experimental.SuperBuilder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class OrderSummaryDTO {
    private UUID id;
    private String orderNumber;
    private String trackingNumber;
    private CustomerInfo customer;
    private ShopInfo shop;
    private String status;
    private Integer itemCount;
    private BigDecimal totalAmount;
    private BigDecimal paidAmount;
    private BigDecimal balanceDue;
    private String priority;
    private LocalDateTime promisedDate;
    private LocalDateTime receivedAt;
    private LocalDateTime expectedReadyAt;
    private LocalDateTime actualReadyAt;
    private LocalDateTime completedAt;
    private String createdBy;
    private LocalDateTime createdAt;
    private List<String> tags;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CustomerInfo {
        private UUID id;
        private String name;
        private String phone;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ShopInfo {
        private UUID id;
        private String name;
    }
}
