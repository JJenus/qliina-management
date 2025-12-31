package com.jjenus.qliina_management.quality.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class DefectFilter {
    private String status;
    private String severity;
    private LocalDateTime fromDate;
    private LocalDateTime toDate;
    private UUID assignedTo;
    private UUID orderId;
}
