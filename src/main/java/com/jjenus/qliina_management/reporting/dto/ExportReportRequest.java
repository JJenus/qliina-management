package com.jjenus.qliina_management.reporting.dto;

import lombok.Data;
import java.util.Map;

@Data
public class ExportReportRequest {
    private String reportType; // REVENUE, PROFIT_LOSS, AGING, TAX, SALES_BY_SERVICE, EMPLOYEE_PERF
    private String format; // CSV, EXCEL, PDF
    private Map<String, Object> parameters;
    private String timezone;
}
