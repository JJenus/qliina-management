package com.jjenus.qliina_management.reporting.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDate;
import java.util.UUID;

@Data
public class TaxReportRequest {
    @NotNull(message = "startDate is required")
    private LocalDate startDate;
    @NotNull(message = "endDate is required")
    private LocalDate endDate;
    private UUID shopId;
    private String taxType; // SALES, VAT, GST
}
