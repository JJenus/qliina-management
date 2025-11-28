package com.jjenus.qliina_management.customer.dto;

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
public class CustomerSummaryDTO {
    private UUID id;
    private String firstName;
    private String lastName;
    private String phone;
    private String email;
    private Integer totalOrders;
    private BigDecimal totalSpent;
    private BigDecimal averageOrderValue;
    private LocalDateTime lastOrderDate;
    private Integer loyaltyPoints;
    private String loyaltyTier;
    private String rfmSegment;
    private List<String> tags;
    private LocalDateTime createdAt;
}
