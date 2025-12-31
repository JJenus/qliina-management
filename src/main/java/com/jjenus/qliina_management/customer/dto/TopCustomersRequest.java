package com.jjenus.qliina_management.customer.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class TopCustomersRequest {
    private String period;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private String metric;
    private UUID shopId;
    private Integer limit = 10;
}
