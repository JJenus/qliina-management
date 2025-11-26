package com.jjenus.qliina_management.audit.controller;

import com.jjenus.qliina_management.audit.dto.*;
import com.jjenus.qliina_management.audit.service.*;
import com.jjenus.qliina_management.common.PageResponse;
import com.jjenus.qliina_management.common.SuccessResponse;
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

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Tag(name = "Compliance", description = "Compliance and data privacy endpoints")
@RestController
@RequestMapping("/api/v1/{businessId}/compliance")
@RequiredArgsConstructor
public class ComplianceController {
    
    private final RetentionService retentionService;
    private final ConsentService consentService;
    private final DataSubjectRequestService dsrService;
    private final SecurityEventService securityEventService;
    private final ComplianceReportService reportService;
    
    // ==================== Data Retention ====================
    
    @Operation(summary = "List retention policies", description = "Get all data retention policies")
    @GetMapping("/retention/policies")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'admin.settings')")
    public ResponseEntity<List<DataRetentionPolicyDTO>> getRetentionPolicies(@PathVariable UUID businessId) {
        return ResponseEntity.ok(retentionService.getPolicies(businessId));
    }
    
    @Operation(summary = "Create retention policy", description = "Create a data retention policy")
    @PostMapping("/retention/policies")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'admin.settings')")
    public ResponseEntity<DataRetentionPolicyDTO> createRetentionPolicy(
            @PathVariable UUID businessId,
            @Valid @RequestBody CreateRetentionPolicyRequest request) {
        return ResponseEntity.ok(retentionService.createPolicy(businessId, request));
    }
    
    @Operation(summary = "Update retention policy", description = "Update a data retention policy")
    @PutMapping("/retention/policies/{policyId}")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'admin.settings')")
    public ResponseEntity<DataRetentionPolicyDTO> updateRetentionPolicy(
            @PathVariable UUID businessId,
            @PathVariable UUID policyId,
            @Valid @RequestBody CreateRetentionPolicyRequest request) {
        return ResponseEntity.ok(retentionService.updatePolicy(policyId, request));
    }
    
    @Operation(summary = "Delete retention policy", description = "Delete a data retention policy")
    @DeleteMapping("/retention/policies/{policyId}")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'admin.settings')")
    public ResponseEntity<SuccessResponse> deleteRetentionPolicy(
            @PathVariable UUID businessId,
            @PathVariable UUID policyId) {
        retentionService.deletePolicy(policyId);
        return ResponseEntity.ok(SuccessResponse.of("Policy deleted successfully"));
    }
    
    @Operation(summary = "Trigger cleanup", description = "Manually trigger data retention cleanup")
    @PostMapping("/retention/cleanup")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'admin.settings')")
    public ResponseEntity<CleanupResultDTO> triggerCleanup(@PathVariable UUID businessId) {
        return ResponseEntity.ok(retentionService.triggerCleanup(businessId));
    }
    
    // ==================== Consent Management ====================
    
    @Operation(summary = "Get customer consents", description = "Get all consents for a customer")
    @GetMapping("/consent/{customerId}")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'customer.view')")
    public ResponseEntity<List<ConsentDTO>> getCustomerConsents(
            @PathVariable UUID businessId,
            @PathVariable UUID customerId) {
        return ResponseEntity.ok(consentService.getCustomerConsents(customerId));
    }
    
    @Operation(summary = "Record consent", description = "Record customer consent")
    @PostMapping("/consent/{customerId}")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'customer.update')")
    public ResponseEntity<ConsentDTO> recordConsent(
            @PathVariable UUID businessId,
            @PathVariable UUID customerId,
            @Valid @RequestBody ConsentRequest request) {
        return ResponseEntity.ok(consentService.recordConsent(businessId, customerId, request));
    }
    
    @Operation(summary = "Revoke consent", description = "Revoke customer consent")
    @DeleteMapping("/consent/{customerId}/{consentType}")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'customer.update')")
    public ResponseEntity<SuccessResponse> revokeConsent(
            @PathVariable UUID businessId,
            @PathVariable UUID customerId,
            @PathVariable String consentType,
            Principal principal) {
        UUID userId = getCurrentUserId(principal);
        consentService.revokeConsent(customerId, consentType, userId);
        return ResponseEntity.ok(SuccessResponse.of("Consent revoked successfully"));
    }
    
    // ==================== Data Subject Requests ====================
    
    @Operation(summary = "List DSRs", description = "Get paginated list of data subject requests")
    @GetMapping("/dsr")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'admin.audit')")
    public ResponseEntity<PageResponse<DataSubjectRequestDTO>> listDataSubjectRequests(
            @PathVariable UUID businessId,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toDate,
            @PageableDefault(size = 20, sort = "submittedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(dsrService.listRequests(
            businessId, customerId, type, status, fromDate, toDate, pageable));
    }
    
    @Operation(summary = "Create DSR", description = "Create a new data subject request")
    @PostMapping("/dsr")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'customer.create')")
    public ResponseEntity<DataSubjectRequestDTO> createDataSubjectRequest(
            @PathVariable UUID businessId,
            @Valid @RequestBody CreateDataSubjectRequest request) {
        return ResponseEntity.ok(dsrService.createRequest(businessId, request));
    }
    
    @Operation(summary = "Get DSR", description = "Get data subject request details")
    @GetMapping("/dsr/{requestId}")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'admin.audit')")
    public ResponseEntity<DataSubjectRequestDTO> getDataSubjectRequest(
            @PathVariable UUID businessId,
            @PathVariable UUID requestId) {
        return ResponseEntity.ok(dsrService.getRequest(requestId));
    }
    
    @Operation(summary = "Update DSR", description = "Update data subject request")
    @PutMapping("/dsr/{requestId}")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'admin.audit')")
    public ResponseEntity<DataSubjectRequestDTO> updateDataSubjectRequest(
            @PathVariable UUID businessId,
            @PathVariable UUID requestId,
            @Valid @RequestBody UpdateDataSubjectRequest request) {
        return ResponseEntity.ok(dsrService.updateRequest(requestId, request));
    }
    
    @Operation(summary = "Process DSR", description = "Process a data subject request")
    @PostMapping("/dsr/{requestId}/process")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'admin.audit')")
    public ResponseEntity<DataSubjectRequestDTO> processDataSubjectRequest(
            @PathVariable UUID businessId,
            @PathVariable UUID requestId) {
        return ResponseEntity.ok(dsrService.processRequest(requestId));
    }
    
    // ==================== Security Events ====================
    
    @Operation(summary = "List security events", description = "Get paginated list of security events")
    @GetMapping("/security/events")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'admin.audit')")
    public ResponseEntity<PageResponse<SecurityEventDTO>> getSecurityEvents(
            @PathVariable UUID businessId,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toDate,
            @RequestParam(required = false) String ipAddress,
            @PageableDefault(size = 20, sort = "timestamp", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(securityEventService.getSecurityEvents(
            businessId, userId, eventType, severity, fromDate, toDate, ipAddress, pageable));
    }
    
    @Operation(summary = "Block IP", description = "Block an IP address")
    @PostMapping("/security/block/{ipAddress}")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'admin.settings')")
    public ResponseEntity<SuccessResponse> blockIP(
            @PathVariable UUID businessId,
            @PathVariable String ipAddress,
            @RequestParam String reason) {
        securityEventService.blockIP(ipAddress, reason);
        return ResponseEntity.ok(SuccessResponse.of("IP blocked successfully"));
    }
    
    @Operation(summary = "Resolve block", description = "Resolve a blocked security event")
    @PostMapping("/security/resolve/{eventId}")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'admin.settings')")
    public ResponseEntity<SuccessResponse> resolveBlock(
            @PathVariable UUID businessId,
            @PathVariable UUID eventId) {
        securityEventService.resolveBlock(eventId);
        return ResponseEntity.ok(SuccessResponse.of("Block resolved successfully"));
    }
    
    // ==================== Compliance Reports ====================
    
    @Operation(summary = "Generate GDPR report", description = "Generate GDPR compliance report")
    @PostMapping("/reports/gdpr")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'admin.audit')")
    public ResponseEntity<ComplianceReportDTO> generateGDPRReport(@PathVariable UUID businessId) {
        return ResponseEntity.ok(reportService.generateGDPRReport(businessId));
    }
    
    @Operation(summary = "List compliance reports", description = "Get paginated list of compliance reports")
    @GetMapping("/reports")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'admin.audit')")
    public ResponseEntity<PageResponse<ComplianceReportDTO>> listComplianceReports(
            @PathVariable UUID businessId,
            @RequestParam(required = false) String reportType,
            @PageableDefault(size = 20, sort = "generatedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(reportService.listReports(businessId, reportType, pageable));
    }
    
    @Operation(summary = "Get compliance report", description = "Get compliance report details")
    @GetMapping("/reports/{reportId}")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'admin.audit')")
    public ResponseEntity<ComplianceReportDTO> getComplianceReport(
            @PathVariable UUID businessId,
            @PathVariable UUID reportId) {
        return ResponseEntity.ok(reportService.getReport(reportId));
    }
    
    @Operation(summary = "Download report", description = "Download compliance report file")
    @GetMapping("/reports/{reportId}/download")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'admin.audit')")
    public ResponseEntity<byte[]> downloadReport(
            @PathVariable UUID businessId,
            @PathVariable UUID reportId) {
        byte[] data = reportService.downloadReport(reportId);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"compliance_report.pdf\"")
            .contentType(MediaType.APPLICATION_PDF)
            .body(data);
    }
    
    private UUID getCurrentUserId(Principal principal) {
        // In a real implementation, get from SecurityContext
        return UUID.fromString("00000000-0000-0000-0000-000000000000");
    }
}
