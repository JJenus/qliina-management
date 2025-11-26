package com.jjenus.qliina_management.audit.controller;

import com.jjenus.qliina_management.audit.dto.AuditLogDTO;
import com.jjenus.qliina_management.audit.dto.AuditLogFilter;
import com.jjenus.qliina_management.audit.dto.AuditSummaryDTO;
import com.jjenus.qliina_management.audit.service.AuditService;
import com.jjenus.qliina_management.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Tag(name = "Audit", description = "Audit logging endpoints")
@RestController
@RequestMapping("/api/v1/{businessId}/audit")
@RequiredArgsConstructor
public class AuditController {
    
    private final AuditService auditService;
    
    @Operation(summary = "Get audit logs", description = "Get paginated audit logs with filters")
    @GetMapping("/logs")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'admin.audit')")
    public ResponseEntity<PageResponse<AuditLogDTO>> getAuditLogs(
            @PathVariable UUID businessId,
            @ModelAttribute AuditLogFilter filter,
            @PageableDefault(size = 20, sort = "timestamp", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(auditService.getAuditLogs(businessId, filter, pageable));
    }
    
    @Operation(summary = "Export audit logs", description = "Export audit logs to CSV")
    @GetMapping("/logs/export")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'admin.audit')")
    public ResponseEntity<byte[]> exportAuditLogs(
            @PathVariable UUID businessId,
            @ModelAttribute AuditLogFilter filter) {
        
        byte[] data = auditService.exportAuditLogs(businessId, filter);
        
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"audit_logs.csv\"")
            .contentType(MediaType.parseMediaType("text/csv"))
            .body(data);
    }
    
    @Operation(summary = "Get entity history", description = "Get audit history for a specific entity")
    @GetMapping("/entity/{entityType}/{entityId}")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'admin.audit')")
    public ResponseEntity<PageResponse<AuditLogDTO>> getEntityHistory(
            @PathVariable UUID businessId,
            @PathVariable String entityType,
            @PathVariable UUID entityId,
            @PageableDefault(size = 20, sort = "timestamp", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(auditService.getEntityHistory(entityType, entityId, pageable));
    }
    
    @Operation(summary = "Get user activity", description = "Get audit logs for a specific user")
    @GetMapping("/user/{userId}")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'admin.audit')")
    public ResponseEntity<PageResponse<AuditLogDTO>> getUserActivity(
            @PathVariable UUID businessId,
            @PathVariable UUID userId,
            @PageableDefault(size = 20, sort = "timestamp", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(auditService.getUserActivity(userId, pageable));
    }
    
    @Operation(summary = "Get audit summary", description = "Get summary of audit activity")
    @GetMapping("/summary")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'admin.audit')")
    public ResponseEntity<AuditSummaryDTO> getAuditSummary(
            @PathVariable UUID businessId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        return ResponseEntity.ok(auditService.getAuditSummary(businessId, startDate, endDate));
    }
}
