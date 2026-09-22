package com.jjenus.qliina_management.complaint.controller;

import com.jjenus.qliina_management.common.PageResponse;
import com.jjenus.qliina_management.common.RequireClockIn;
import com.jjenus.qliina_management.common.util.SecurityContextUtil;
import com.jjenus.qliina_management.complaint.dto.ComplaintDTO;
import com.jjenus.qliina_management.complaint.dto.CreateComplaintRequest;
import com.jjenus.qliina_management.complaint.dto.UpdateComplaintStatusRequest;
import com.jjenus.qliina_management.complaint.model.ComplaintSeverity;
import com.jjenus.qliina_management.complaint.model.ComplaintStatus;
import com.jjenus.qliina_management.complaint.service.ComplaintService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Tenant complaint / support-ticket endpoints (business-scope permissions,
 * mirrored from the Expense domain). The platform inbox lives in
 * {@link AdminComplaintController}.
 */
@Tag(name = "Complaints", description = "Customer complaints and support tickets")
@RestController
@RequestMapping("/api/v1/{businessId}/complaints")
@RequiredArgsConstructor
@RequireClockIn
public class ComplaintController {

    private final ComplaintService complaintService;

    @Operation(summary = "List complaints with optional status/severity filters")
    @GetMapping
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'complaint.view')")
    public ResponseEntity<PageResponse<ComplaintDTO>> listComplaints(
            @PathVariable UUID businessId,
            @RequestParam(required = false) ComplaintStatus status,
            @RequestParam(required = false) ComplaintSeverity severity,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(
                complaintService.list(businessId, status, severity, page, size));
    }

    @Operation(summary = "Get a single complaint")
    @GetMapping("/{complaintId}")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'complaint.view')")
    public ResponseEntity<ComplaintDTO> getComplaint(
            @PathVariable UUID businessId,
            @PathVariable UUID complaintId) {
        return ResponseEntity.ok(complaintService.get(businessId, complaintId));
    }

    @Operation(summary = "Log a new customer complaint")
    @PostMapping
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'complaint.create')")
    public ResponseEntity<ComplaintDTO> createComplaint(
            @PathVariable UUID businessId,
            @Valid @RequestBody CreateComplaintRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(complaintService.create(businessId, req));
    }

    @Operation(summary = "Update complaint status (triage / resolve)")
    @PatchMapping("/{complaintId}/status")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'complaint.resolve')")
    public ResponseEntity<ComplaintDTO> updateStatus(
            @PathVariable UUID businessId,
            @PathVariable UUID complaintId,
            @Valid @RequestBody UpdateComplaintStatusRequest req) {
        return ResponseEntity.ok(complaintService.updateStatus(
                businessId, complaintId, req, SecurityContextUtil.requireUserId()));
    }
}