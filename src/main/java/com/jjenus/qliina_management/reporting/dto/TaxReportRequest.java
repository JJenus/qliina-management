package com.jjenus.qliina_management.reporting.dto;

import lombok.Data;
import java.time.LocalDate;
import java.util.UUID;

@Data
public class TaxReportRequest {
    private LocalDate startDate;
    private LocalDate endDate;
    private UUID shopId;
    private String taxType; // SALES, VAT, GST
}
