package com.jjenus.qliina_management.reporting.dto;

import lombok.Data;
import java.time.LocalDate;
import java.util.UUID;

@Data
public class SalesByServiceRequest {
    private LocalDate startDate;
    private LocalDate endDate;
    private UUID shopId;
    private String groupBy; // DAY, WEEK, MONTH
}
