package com.jjenus.qliina_management.customer.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class CustomerFilter {
    private String search;
    private String segment;
    private Double minSpend;
    private Double maxSpend;
    private Integer minOrders;
    private LocalDateTime lastOrderFrom;
    private LocalDateTime lastOrderTo;
    private List<String> tags;
    private String loyaltyTier;
}
