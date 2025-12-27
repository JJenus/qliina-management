package com.jjenus.qliina_management.quality.controller;

import com.jjenus.qliina_management.common.PageResponse;
import com.jjenus.qliina_management.quality.dto.*;
import com.jjenus.qliina_management.quality.service.QualityService;
import io.swagger.v3.oas.annotations.Operation;
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
import java.util.List;
import java.util.UUID;

@Tag(name = "Quality Control", description = "Quality control endpoints")
@RestController
@RequestMapping("/api/v1/{businessId}/quality")
@RequiredArgsConstructor
public class QualityController {
    
    private final QualityService qualityService;
    
    @Operation(summary = "List checklists", description = "Get all quality checklists")
    @GetMapping("/checklists")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'quality.view')")
    public ResponseEntity<List<QualityChecklistDTO>> listChecklists(@PathVariable UUID businessId) {
        return ResponseEntity.ok(qualityService.listChecklists(businessId));
    }
    
    @Operation(summary = "Get checklist", description = "Get checklist by ID")
    @GetMapping("/checklists/{checklistId}")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'quality.view')")
    public ResponseEntity<QualityChecklistDTO> getChecklist(
            @PathVariable UUID businessId,
            @PathVariable UUID checklistId) {
        return ResponseEntity.ok(qualityService.getChecklist(checklistId));
    }
    
    @Operation(summary = "Create checklist", description = "Create a new quality checklist")
    @PostMapping("/checklists")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'quality.manage')")
    public ResponseEntity<QualityChecklistDTO> createChecklist(
            @PathVariable UUID businessId,
            @Valid @RequestBody CreateChecklistRequest request) {
        return ResponseEntity.ok(qualityService.createChecklist(businessId, request));
    }
    
    @Operation(summary = "Update checklist", description = "Update an existing checklist")
    @PutMapping("/checklists/{checklistId}")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'quality.manage')")
    public ResponseEntity<QualityChecklistDTO> updateChecklist(
            @PathVariable UUID businessId,
            @PathVariable UUID checklistId,
            @Valid @RequestBody UpdateChecklistRequest request) {
        return ResponseEntity.ok(qualityService.updateChecklist(checklistId, request));
    }
    
    @Operation(summary = "Perform quality check", description = "Perform quality check on an order item")
    @PostMapping("/orders/{orderId}/items/{itemId}/quality-check")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'quality.check')")
    public ResponseEntity<QualityCheckResultDTO> performQualityCheck(
            @PathVariable UUID businessId,
            @PathVariable UUID orderId,
            @PathVariable UUID itemId,
            @Valid @RequestBody QualityCheckRequest request) {
        return ResponseEntity.ok(qualityService.performQualityCheck(businessId, orderId, itemId, request));
    }
    
    @Operation(summary = "Report defect", description = "Report a defect on an order item")
    @PostMapping("/orders/{orderId}/items/{itemId}/defect")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'quality.check')")
    public ResponseEntity<DefectDTO> reportDefect(
            @PathVariable UUID businessId,
            @PathVariable UUID orderId,
            @PathVariable UUID itemId,
            @Valid @RequestBody ReportDefectRequest request) {
        return ResponseEntity.ok(qualityService.reportDefect(businessId, orderId, itemId, request));
    }
    
    @Operation(summary = "Update defect", description = "Update defect status and resolution")
    @PutMapping("/defects/{defectId}")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'quality.manage')")
    public ResponseEntity<DefectDTO> updateDefect(
            @PathVariable UUID businessId,
            @PathVariable UUID defectId,
            @Valid @RequestBody UpdateDefectRequest request) {
        return ResponseEntity.ok(qualityService.updateDefect(defectId, request));
    }
    
    @Operation(summary = "List defects", description = "Get paginated list of defects")
    @GetMapping("/defects")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'quality.view')")
    public ResponseEntity<PageResponse<DefectDTO>> listDefects(
            @PathVariable UUID businessId,
            @ModelAttribute DefectFilter filter,
            @PageableDefault(size = 20, sort = "reportedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(qualityService.listDefects(businessId, filter, pageable));
    }
    
    @Operation(summary = "Employee scorecards", description = "Get quality scorecards for all employees")
    @GetMapping("/scorecards/employees")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'quality.view')")
    public ResponseEntity<List<EmployeeScorecardDTO>> getEmployeeScorecards(
            @PathVariable UUID businessId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(qualityService.getEmployeeScorecards(businessId, startDate, endDate));
    }
    
    @Operation(summary = "Employee scorecard", description = "Get quality scorecard for a specific employee")
    @GetMapping("/scorecards/employees/{employeeId}")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'quality.view')")
    public ResponseEntity<EmployeeScorecardDTO> getEmployeeScorecard(
            @PathVariable UUID businessId,
            @PathVariable UUID employeeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(qualityService.getEmployeeScorecard(businessId, employeeId, startDate, endDate));
    }
}
