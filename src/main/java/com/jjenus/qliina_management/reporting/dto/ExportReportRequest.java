package com.jjenus.qliina_management.reporting.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.util.Map;

@Data
public class ExportReportRequest {
    @NotBlank(message = "reportType is required")
    private String reportType; // REVENUE, PROFIT_LOSS, AGING, TAX, SALES_BY_SERVICE, EMPLOYEE_PERF
    @NotBlank(message = "format is required")
    private String format; // CSV, EXCEL, PDF
    private Map<String, Object> parameters;
    private String timezone;
}
