package com.jjenus.qliina_management.reporting.controller;

import com.jjenus.qliina_management.common.ErrorResponse;
import com.jjenus.qliina_management.reporting.dto.*;
import com.jjenus.qliina_management.reporting.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
    
    @Operation(
        summary = "Get dashboard summary",
        description = "Get KPI dashboard summary for business with optional shop filter"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved dashboard"),
        @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/dashboard")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'report.view.operational')")
    public ResponseEntity<DashboardSummaryDTO> getDashboard(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Parameter(description = "Shop ID to filter dashboard data")
            @RequestParam(required = false) UUID shopId) {
        return ResponseEntity.ok(dashboardService.getDashboardSummary(businessId, shopId));
    }
    
    @Operation(
        summary = "Revenue report",
        description = "Generate revenue report with customizable grouping and filtering"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully generated revenue report"),
        @ApiResponse(responseCode = "400", description = "Invalid request parameters",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/revenue")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'report.view.financial')")
    public ResponseEntity<RevenueReportDTO> getRevenueReport(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Valid @ModelAttribute RevenueReportRequest request) {
        return ResponseEntity.ok(revenueService.generateRevenueReport(businessId, request));
    }
    
    @Operation(
        summary = "Profit & Loss statement",
        description = "Generate Profit & Loss statement for a date range"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully generated P&L statement"),
        @ApiResponse(responseCode = "400", description = "Invalid date range",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/profit-loss")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'report.view.financial')")
    public ResponseEntity<ProfitLossDTO> getProfitLoss(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Valid @ModelAttribute DateRangeRequest request) {
        return ResponseEntity.ok(financialService.generateProfitLoss(businessId, request));
    }
    
    @Operation(
        summary = "Accounts receivable aging",
        description = "Generate aging report for outstanding receivables"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully generated aging report"),
        @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/aging")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'report.view.financial')")
    public ResponseEntity<AgingReportDTO> getAgingReport(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId) {
        return ResponseEntity.ok(agingService.generateAgingReport(businessId));
    }
    
    @Operation(
        summary = "Tax report",
        description = "Generate tax report for a date range"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully generated tax report"),
        @ApiResponse(responseCode = "400", description = "Invalid request parameters",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Business configuration not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/tax")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'report.view.financial')")
    public ResponseEntity<TaxReportDTO> getTaxReport(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Valid @ModelAttribute TaxReportRequest request) {
        return ResponseEntity.ok(taxService.generateTaxReport(businessId, request));
    }
    
    @Operation(
        summary = "Sales by service",
        description = "Generate sales breakdown by service type"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully generated sales report"),
        @ApiResponse(responseCode = "400", description = "Invalid request parameters",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/sales-by-service")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'report.view.operational')")
    public ResponseEntity<SalesByServiceDTO> getSalesByService(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Valid @ModelAttribute SalesByServiceRequest request) {
        return ResponseEntity.ok(salesService.generateSalesByServiceReport(businessId, request));
    }
    
    @Operation(
        summary = "Employee performance",
        description = "Generate employee performance report for a date range"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully generated performance report"),
        @ApiResponse(responseCode = "400", description = "Invalid request parameters",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Employee not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/employee-performance")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'report.view.operational')")
    public ResponseEntity<List<EmployeePerfDTO>> getEmployeePerformance(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Valid @ModelAttribute EmployeePerfRequest request) {
        return ResponseEntity.ok(employeeService.generateEmployeePerformanceReport(businessId, request));
    }
    
    @Operation(
        summary = "Export report",
        description = "Export report in specified format (CSV, EXCEL, PDF)"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "File download started"),
        @ApiResponse(responseCode = "400", description = "Invalid export format or parameters",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Report data not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/export")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'report.export')")
    public ResponseEntity<byte[]> exportReport(
            @Parameter(description = "Business ID", required = true)
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