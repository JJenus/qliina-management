package com.jjenus.qliina_management.quality.controller;

import com.jjenus.qliina_management.common.PageResponse;
import com.jjenus.qliina_management.common.SuccessResponse;
import com.jjenus.qliina_management.common.ErrorResponse;
import com.jjenus.qliina_management.quality.dto.*;
import com.jjenus.qliina_management.quality.service.QualityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Tag(name = "Quality Control", description = "Complete quality control endpoints for checklists, inspections, and defect tracking")
@RestController
@RequestMapping("/api/v1/{businessId}/quality")
@RequiredArgsConstructor
public class QualityController {
    
    private final QualityService qualityService;
    
    // ==================== Checklist Management ====================
    
    @Operation(
        summary = "List checklists",
        description = "Get all active quality checklists for the business"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved checklists"),
        @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/checklists")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'quality.view')")
    public ResponseEntity<List<QualityChecklistDTO>> listChecklists(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId) {
        return ResponseEntity.ok(qualityService.listChecklists(businessId));
    }
    
    @Operation(
        summary = "Get checklist",
        description = "Get quality checklist details by ID"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved checklist"),
        @ApiResponse(responseCode = "404", description = "Checklist not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/checklists/{checklistId}")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'quality.view')")
    public ResponseEntity<QualityChecklistDTO> getChecklist(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Parameter(description = "Checklist ID", required = true)
            @PathVariable UUID checklistId) {
        return ResponseEntity.ok(qualityService.getChecklist(checklistId));
    }
    
    @Operation(
        summary = "Create checklist",
        description = "Create a new quality checklist with items"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Checklist created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid checklist data",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/checklists")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'quality.manage')")
    public ResponseEntity<QualityChecklistDTO> createChecklist(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Valid @RequestBody CreateChecklistRequest request) {
        return ResponseEntity.ok(qualityService.createChecklist(businessId, request));
    }
    
    @Operation(
        summary = "Update checklist",
        description = "Update an existing quality checklist"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Checklist updated successfully"),
        @ApiResponse(responseCode = "404", description = "Checklist not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping("/checklists/{checklistId}")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'quality.manage')")
    public ResponseEntity<QualityChecklistDTO> updateChecklist(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Parameter(description = "Checklist ID", required = true)
            @PathVariable UUID checklistId,
            
            @Valid @RequestBody UpdateChecklistRequest request) {
        return ResponseEntity.ok(qualityService.updateChecklist(checklistId, request));
    }
    
    // ==================== Quality Checks ====================
    
    @Operation(
        summary = "Perform quality check",
        description = "Perform a quality check on an order item with checklist results and defect reporting"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Quality check completed"),
        @ApiResponse(responseCode = "404", description = "Order or item not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid check data",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/orders/{orderId}/items/{itemId}/quality-check")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'quality.check')")
    public ResponseEntity<QualityCheckResultDTO> performQualityCheck(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Parameter(description = "Order ID", required = true)
            @PathVariable UUID orderId,
            
            @Parameter(description = "Order Item ID", required = true)
            @PathVariable UUID itemId,
            
            @Valid @RequestBody QualityCheckRequest request) {
        return ResponseEntity.ok(qualityService.performQualityCheck(businessId, orderId, itemId, request));
    }
    
    // ==================== Defect Management ====================
    
    @Operation(
        summary = "Report defect",
        description = "Report a defect on an order item"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Defect reported successfully"),
        @ApiResponse(responseCode = "404", description = "Order or item not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid defect data",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/orders/{orderId}/items/{itemId}/defect")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'quality.check')")
    public ResponseEntity<DefectDTO> reportDefect(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Parameter(description = "Order ID", required = true)
            @PathVariable UUID orderId,
            
            @Parameter(description = "Order Item ID", required = true)
            @PathVariable UUID itemId,
            
            @Valid @RequestBody ReportDefectRequest request) {
        return ResponseEntity.ok(qualityService.reportDefect(businessId, orderId, itemId, request));
    }
    
    @Operation(
        summary = "Update defect",
        description = "Update defect status, resolution, and compensation"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Defect updated successfully"),
        @ApiResponse(responseCode = "404", description = "Defect not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid update data",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping("/defects/{defectId}")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'quality.manage')")
    public ResponseEntity<DefectDTO> updateDefect(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Parameter(description = "Defect ID", required = true)
            @PathVariable UUID defectId,
            
            @Valid @RequestBody UpdateDefectRequest request) {
        return ResponseEntity.ok(qualityService.updateDefect(defectId, request));
    }
    
    @Operation(
        summary = "List defects",
        description = "Get paginated list of defects with filters"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved defects"),
        @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/defects")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'quality.view')")
    public ResponseEntity<PageResponse<DefectDTO>> listDefects(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Parameter(description = "Filter criteria for defects")
            @ModelAttribute DefectFilter filter,
            
            @Parameter(description = "Pagination parameters")
            @PageableDefault(size = 20, sort = "reportedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(qualityService.listDefects(businessId, filter, pageable));
    }
    
    @Operation(
        summary = "Resolve defect",
        description = "Mark a defect as resolved with resolution notes"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Defect resolved successfully"),
        @ApiResponse(responseCode = "404", description = "Defect not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/defects/{defectId}/resolve")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'quality.manage')")
    public ResponseEntity<SuccessResponse> resolveDefect(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Parameter(description = "Defect ID", required = true)
            @PathVariable UUID defectId,
            
            @RequestParam String resolution) {
        // This method would need to be added to the service
        // For now, using existing updateDefect method
        UpdateDefectRequest request = new UpdateDefectRequest();
        request.setStatus("RESOLVED");
        request.setResolution(resolution);
        qualityService.updateDefect(defectId, request);
        return ResponseEntity.ok(SuccessResponse.of("Defect resolved successfully"));
    }
    
    // ==================== Employee Scorecards ====================
    
    @Operation(
        summary = "Employee scorecards",
        description = "Get quality scorecards for all employees over a date range"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved scorecards"),
        @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/scorecards/employees")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'quality.view')")
    public ResponseEntity<List<EmployeeScorecardDTO>> getEmployeeScorecards(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Parameter(description = "Start date (ISO format)", required = true, example = "2026-03-01")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            
            @Parameter(description = "End date (ISO format)", required = true, example = "2026-03-31")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(qualityService.getEmployeeScorecards(businessId, startDate, endDate));
    }
    
    @Operation(
        summary = "Employee scorecard",
        description = "Get quality scorecard for a specific employee"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved scorecard"),
        @ApiResponse(responseCode = "404", description = "Employee not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/scorecards/employees/{employeeId}")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'quality.view')")
    public ResponseEntity<EmployeeScorecardDTO> getEmployeeScorecard(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Parameter(description = "Employee ID", required = true)
            @PathVariable UUID employeeId,
            
            @Parameter(description = "Start date (ISO format)", required = true, example = "2026-03-01")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            
            @Parameter(description = "End date (ISO format)", required = true, example = "2026-03-31")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(qualityService.getEmployeeScorecard(businessId, employeeId, startDate, endDate));
    }
    
    // ==================== Analytics ====================
    
    @Operation(
        summary = "Get defect type distribution",
        description = "Get distribution of defects by type over a date range"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved distribution"),
        @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/analytics/defects/by-type")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'quality.view')")
    public ResponseEntity<Map<String, Long>> getDefectTypeDistribution(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Parameter(description = "Start date (ISO format)", required = true)
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            
            @Parameter(description = "End date (ISO format)", required = true)
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {

        return ResponseEntity.ok(qualityService.getDefectTypeDistribution(businessId, startDate, endDate));
    }
    
    @Operation(
        summary = "Get severity distribution",
        description = "Get distribution of defects by severity over a date range"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved distribution"),
        @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/analytics/defects/by-severity")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'quality.view')")
    public ResponseEntity<Map<String, Long>> getSeverityDistribution(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Parameter(description = "Start date (ISO format)", required = true)
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            
            @Parameter(description = "End date (ISO format)", required = true)
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {

        return ResponseEntity.ok(qualityService.getSeverityDistribution(businessId, startDate, endDate));
    }
}