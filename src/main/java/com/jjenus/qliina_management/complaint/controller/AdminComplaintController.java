package com.jjenus.qliina_management.complaint.controller;

import com.jjenus.qliina_management.common.PageResponse;
import com.jjenus.qliina_management.common.util.SecurityContextUtil;
import com.jjenus.qliina_management.complaint.dto.ComplaintDTO;
import com.jjenus.qliina_management.complaint.dto.UpdateComplaintStatusRequest;
import com.jjenus.qliina_management.complaint.model.ComplaintSeverity;
import com.jjenus.qliina_management.complaint.model.ComplaintStatus;
import com.jjenus.qliina_management.complaint.service.ComplaintService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Platform complaint inbox — cross-tenant triage. Read side is gated by
 * {@code platform.complaints.view} (SUPPORT_AGENT / READONLY_AUDITOR are
 * view-only), writes by {@code platform.complaints.manage} (PLATFORM_ADMIN).
 */
@Tag(name = "Platform Complaints", description = "Cross-tenant complaint inbox (platform staff)")
@RestController
@RequestMapping("/api/v1/admin/complaints")
@RequiredArgsConstructor
public class AdminComplaintController {

    private final ComplaintService complaintService;

    @Operation(summary = "List complaints across businesses (filters optional)")
    @GetMapping
    @PreAuthorize("hasPermission(null, 'PLATFORM', 'platform.complaints.view')")
    public ResponseEntity<PageResponse<ComplaintDTO>> listComplaints(
            @RequestParam(required = false) UUID businessId,
            @RequestParam(required = false) ComplaintStatus status,
            @RequestParam(required = false) ComplaintSeverity severity,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(
                complaintService.listPlatform(businessId, status, severity, page, size));
    }

    @Operation(summary = "Get a single complaint by id (any business)")
    @GetMapping("/{complaintId}")
    @PreAuthorize("hasPermission(null, 'PLATFORM', 'platform.complaints.view')")
    public ResponseEntity<ComplaintDTO> getComplaint(@PathVariable UUID complaintId) {
        return ResponseEntity.ok(complaintService.getPlatform(complaintId));
    }

    @Operation(summary = "Triage / resolve a complaint (platform staff)")
    @PatchMapping("/{complaintId}/status")
    @PreAuthorize("hasPermission(null, 'PLATFORM', 'platform.complaints.manage')")
    public ResponseEntity<ComplaintDTO> updateStatus(
            @PathVariable UUID complaintId,
            @Valid @RequestBody UpdateComplaintStatusRequest req) {
        return ResponseEntity.ok(complaintService.updateStatusPlatform(
                complaintId, req, SecurityContextUtil.requireUserId()));
    }
}