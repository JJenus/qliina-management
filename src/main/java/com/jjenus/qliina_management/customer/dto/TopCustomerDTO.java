package com.jjenus.qliina_management.customer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopCustomerDTO {
    private Integer rank;
    private UUID customerId;
    private String customerName;
    private String phone;
    private Double metric;
    private Integer orders;
    private BigDecimal totalSpent;
    private BigDecimal averageOrderValue;
    private LocalDateTime lastOrderDate;
}
