package com.jjenus.qliina_management.reporting.controller;

import com.jjenus.qliina_management.reporting.dto.*;
import com.jjenus.qliina_management.reporting.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Tag(name = "Reports", description = "Reporting and analytics endpoints")
@RestController
@RequestMapping("/api/v1/{businessId}/reports")
@RequiredArgsConstructor
public class ReportController {
    
    private final RevenueReportService revenueService;
    private final FinancialReportService financialService;
    private final AgingReportService agingService;
    private final TaxReportService taxService;
    private final SalesReportService salesService;
    private final EmployeeReportService employeeService;
    private final ReportExportService exportService;
    private final DashboardService dashboardService;
    
    @Operation(summary = "Get dashboard summary", description = "Get KPI dashboard summary")
    @GetMapping("/dashboard")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'report.view.operational')")
    public ResponseEntity<DashboardSummaryDTO> getDashboard(
            @PathVariable UUID businessId,
            @RequestParam(required = false) UUID shopId) {
        return ResponseEntity.ok(dashboardService.getDashboardSummary(businessId, shopId));
    }
    
    @Operation(summary = "Revenue report", description = "Generate revenue report")
    @GetMapping("/revenue")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'report.view.financial')")
    public ResponseEntity<RevenueReportDTO> getRevenueReport(
            @PathVariable UUID businessId,
            @Valid @ModelAttribute RevenueReportRequest request) {
        return ResponseEntity.ok(revenueService.generateRevenueReport(businessId, request));
    }
    
    @Operation(summary = "Profit & Loss statement", description = "Generate P&L statement")
    @GetMapping("/profit-loss")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'report.view.financial')")
    public ResponseEntity<ProfitLossDTO> getProfitLoss(
            @PathVariable UUID businessId,
            @Valid @ModelAttribute DateRangeRequest request) {
        return ResponseEntity.ok(financialService.generateProfitLoss(businessId, request));
    }
    
    @Operation(summary = "Accounts receivable aging", description = "Generate aging report")
    @GetMapping("/aging")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'report.view.financial')")
    public ResponseEntity<AgingReportDTO> getAgingReport(@PathVariable UUID businessId) {
        return ResponseEntity.ok(agingService.generateAgingReport(businessId));
    }
    
    @Operation(summary = "Tax report", description = "Generate tax report")
    @GetMapping("/tax")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'report.view.financial')")
    public ResponseEntity<TaxReportDTO> getTaxReport(
            @PathVariable UUID businessId,
            @Valid @ModelAttribute TaxReportRequest request) {
        return ResponseEntity.ok(taxService.generateTaxReport(businessId, request));
    }
    
    @Operation(summary = "Sales by service", description = "Generate sales by service report")
    @GetMapping("/sales-by-service")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'report.view.operational')")
    public ResponseEntity<SalesByServiceDTO> getSalesByService(
            @PathVariable UUID businessId,
            @Valid @ModelAttribute SalesByServiceRequest request) {
        return ResponseEntity.ok(salesService.generateSalesByServiceReport(businessId, request));
    }
    
    @Operation(summary = "Employee performance", description = "Generate employee performance report")
    @GetMapping("/employee-performance")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'report.view.operational')")
    public ResponseEntity<List<EmployeePerfDTO>> getEmployeePerformance(
            @PathVariable UUID businessId,
            @Valid @ModelAttribute EmployeePerfRequest request) {
        return ResponseEntity.ok(employeeService.generateEmployeePerformanceReport(businessId, request));
    }
    
    @Operation(summary = "Export report", description = "Export report in specified format")
    @PostMapping("/export")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'report.export')")
    public ResponseEntity<byte[]> exportReport(
            @PathVariable UUID businessId,
            @Valid @RequestBody ExportReportRequest request) {
        
        byte[] data = exportService.exportReport(businessId, request);
        
        String filename = generateFilename(request);
        MediaType mediaType = getMediaType(request.getFormat());
        
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
            .contentType(mediaType)
            .body(data);
    }
    
    private String generateFilename(ExportReportRequest request) {
        String timestamp = LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
        return String.format("%s_%s.%s", 
            request.getReportType().toLowerCase(), 
            timestamp, 
            request.getFormat().toLowerCase());
    }
    
    private MediaType getMediaType(String format) {
        switch (format.toUpperCase()) {
            case "CSV":
                return MediaType.parseMediaType("text/csv");
            case "EXCEL":
                return MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            case "PDF":
                return MediaType.APPLICATION_PDF;
            default:
                return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
