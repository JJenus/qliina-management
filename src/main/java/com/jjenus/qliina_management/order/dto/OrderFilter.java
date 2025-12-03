package com.jjenus.qliina_management.order.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
public class OrderFilter {
    private UUID customerId;
    private UUID shopId;
    private List<String> status;
    private LocalDateTime fromDate;
    private LocalDateTime toDate;
    private Double minAmount;
    private Double maxAmount;
    private String paymentStatus;
    private List<String> serviceType;
    private UUID assignedTo;
    private String priority;
    private String tag;
    private Boolean hasIssue;
}
