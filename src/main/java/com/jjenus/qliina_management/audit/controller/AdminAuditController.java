package com.jjenus.qliina_management.audit.controller;

import com.jjenus.qliina_management.audit.model.AuditLog;
import com.jjenus.qliina_management.audit.model.SecurityEvent;
import com.jjenus.qliina_management.audit.repository.AuditLogRepository;
import com.jjenus.qliina_management.audit.repository.ComplianceReportRepository;
import com.jjenus.qliina_management.audit.repository.ConsentRecordRepository;
import com.jjenus.qliina_management.audit.repository.DataSubjectRequestRepository;
import com.jjenus.qliina_management.audit.repository.DataRetentionPolicyRepository;
import com.jjenus.qliina_management.audit.repository.SecurityEventRepository;
import com.jjenus.qliina_management.common.PageResponse;
import jakarta.persistence.criteria.Predicate;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Cross-tenant audit, security and compliance views for platform staff.
 * READONLY_AUDITOR's primary surface — read-only by design.
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminAuditController {

    private static final String AUDIT = "hasPermission(null, 'PLATFORM', 'platform.audit.view')";

    private final AuditLogRepository auditLogRepository;
    private final SecurityEventRepository securityEventRepository;
    private final ComplianceReportRepository complianceReportRepository;
    private final DataSubjectRequestRepository dataSubjectRequestRepository;
    private final ConsentRecordRepository consentRecordRepository;
    private final DataRetentionPolicyRepository dataRetentionPolicyRepository;

    // ---------------------------------------------------------------------
    // Cross-tenant audit logs
    // ---------------------------------------------------------------------

    @GetMapping("/audit/logs")
    @PreAuthorize(AUDIT)
    public PageResponse<AdminAuditLogRow> logs(
            @RequestParam(required = false) UUID businessId,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) LocalDateTime fromDate,
            @RequestParam(required = false) LocalDateTime toDate,
            @RequestParam(required = false) String ipAddress,
            @PageableDefault(size = 25) Pageable pageable) {

        Sort effective = pageable.getSort().isUnsorted()
                ? Sort.by(Sort.Direction.DESC, "timestamp") : pageable.getSort();
        Pageable p = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), effective);

        Specification<AuditLog> spec = auditSpec(businessId, userId, entityType, action,
                category, severity, fromDate, toDate, ipAddress);
        Page<AuditLog> page = auditLogRepository.findAll(spec, p);
        return PageResponse.from(page.map(AdminAuditController::toLogRow));
    }

    @Builder
    public record AdminAuditLogRow(
            String id, LocalDateTime timestamp, UUID businessId,
            UUID userId, String userName, String userEmail,
            String entityType, UUID entityId, String entityDisplay,
            String action, String category, String severity,
            String oldValue, String newValue,
            String ipAddress, String requestMethod, String requestPath,
            Integer responseStatus, Long executionTimeMs
    ) {}

    private static AdminAuditLogRow toLogRow(AuditLog a) {
        return AdminAuditLogRow.builder()
                .id(a.getId() != null ? a.getId().toString() : null)
                .timestamp(a.getTimestamp())
                .businessId(a.getBusinessId())
                .userId(a.getUserId())
                .userName(a.getUserName())
                .userEmail(a.getUserEmail())
                .entityType(a.getEntityType())
                .entityId(a.getEntityId())
                .entityDisplay(a.getEntityDisplay())
                .action(a.getAction())
                .category(a.getCategory())
                .severity(a.getSeverity() != null ? a.getSeverity().name() : null)
                .oldValue(a.getOldValue())
                .newValue(a.getNewValue())
                .ipAddress(a.getIpAddress())
                .requestMethod(a.getRequestMethod())
                .requestPath(a.getRequestPath())
                .responseStatus(a.getResponseStatus())
                .executionTimeMs(a.getExecutionTimeMs())
                .build();
    }

    @GetMapping("/audit/logs/export")
    @PreAuthorize(AUDIT + " or hasPermission(null, 'PLATFORM', 'platform.audit.export')")
    public ResponseEntity<String> exportLogs(
            @RequestParam(required = false) UUID businessId,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) LocalDateTime fromDate,
            @RequestParam(required = false) LocalDateTime toDate) {

        Specification<AuditLog> spec = auditSpec(businessId, null, entityType, action,
                null, severity, fromDate, toDate, null);
        List<AuditLog> all = auditLogRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "timestamp"));

        StringBuilder csv = new StringBuilder(
                "timestamp,business_id,user_name,user_email,entity_type,action,category,severity,ip_address,request_method,request_path\n");
        for (AuditLog a : all) {
            csv.append(cell(a.getTimestamp())).append(',')
                    .append(cell(a.getBusinessId())).append(',')
                    .append(cell(a.getUserName())).append(',')
                    .append(cell(a.getUserEmail())).append(',')
                    .append(cell(a.getEntityType())).append(',')
                    .append(cell(a.getAction())).append(',')
                    .append(cell(a.getCategory())).append(',')
                    .append(cell(a.getSeverity() != null ? a.getSeverity().name() : "")).append(',')
                    .append(cell(a.getIpAddress())).append(',')
                    .append(cell(a.getRequestMethod())).append(',')
                    .append(cell(a.getRequestPath()))
                    .append('\n');
        }
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=qliina-audit-export.csv")
                .header("Content-Type", "text/csv; charset=utf-8")
                .body(csv.toString());
    }

    // ---------------------------------------------------------------------
    // Security events (platform-wide)
    // ---------------------------------------------------------------------

    @GetMapping("/security-events")
    @PreAuthorize(AUDIT)
    public PageResponse<AdminSecurityEventRow> securityEvents(
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) LocalDateTime fromDate,
            @RequestParam(required = false) LocalDateTime toDate,
            @PageableDefault(size = 25) Pageable pageable) {

        Sort effective = pageable.getSort().isUnsorted()
                ? Sort.by(Sort.Direction.DESC, "timestamp") : pageable.getSort();
        Pageable p = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), effective);

        SecurityEvent.SecurityEventType type = null;
        if (eventType != null && !eventType.isBlank()) {
            try { type = SecurityEvent.SecurityEventType.valueOf(eventType.toUpperCase()); }
            catch (IllegalArgumentException ignored) { /* unknown filter → no match on type */ }
        }
        AuditLog.AuditSeverity sev = null;
        if (severity != null && !severity.isBlank()) {
            try { sev = AuditLog.AuditSeverity.valueOf(severity.toUpperCase()); }
            catch (IllegalArgumentException ignored) { }
        }

        Page<SecurityEvent> page = securityEventRepository.searchEvents(null, null, type, sev,
                fromDate, toDate, null, p);

        return PageResponse.from(page.map(e -> AdminSecurityEventRow.builder()
                .id(e.getId() != null ? e.getId().toString() : null)
                .timestamp(e.getTimestamp())
                .eventType(e.getEventType() != null ? e.getEventType().name() : null)
                .severity(e.getSeverity() != null ? e.getSeverity().name() : null)
                .username(e.getUsername())
                .businessId(e.getBusinessId())
                .ipAddress(e.getIpAddress())
                .blocked(Boolean.TRUE.equals(e.getBlocked()))
                .blockReason(e.getBlockReason())
                .resolvedAt(e.getResolvedAt())
                .build()));
    }

    @Builder
    public record AdminSecurityEventRow(
            String id, LocalDateTime timestamp, String eventType, String severity,
            String username, UUID businessId, String ipAddress,
            boolean blocked, String blockReason, LocalDateTime resolvedAt
    ) {}

    @GetMapping("/security-events/summary")
    @PreAuthorize(AUDIT)
    public ResponseEntity<Map<String, Object>> securitySummary() {
        LocalDateTime d30 = LocalDateTime.now().minusDays(30);
        return ResponseEntity.ok(Map.of(
                "activeBlocks", securityEventRepository.findActiveBlocks().size(),
                "eventsLast30d", securityEventRepository.countByTimestampAfter(d30),
                "failedLogins30d", securityEventRepository.countByEventTypeAndTimestampAfter(
                        SecurityEvent.SecurityEventType.LOGIN_FAILED, d30)
        ));
    }

    // ---------------------------------------------------------------------
    // Compliance overview
    // ---------------------------------------------------------------------

    @GetMapping("/compliance/overview")
    @PreAuthorize(AUDIT)
    public ResponseEntity<Map<String, Object>> complianceOverview() {
        return ResponseEntity.ok(Map.of(
                "complianceReports", complianceReportRepository.count(),
                "openDataSubjectRequests", dataSubjectRequestRepository.count(),
                "consentRecords", consentRecordRepository.count(),
                "retentionPolicies", dataRetentionPolicyRepository.count(),
                "generatedAt", LocalDateTime.now().toString()
        ));
    }

    // ---------------------------------------------------------------------
    // Shared spec builder (all filters optional; businessId nullable = global)
    // ---------------------------------------------------------------------

    private Specification<AuditLog> auditSpec(UUID businessId, UUID userId, String entityType,
                                              String action, String category, String severity,
                                              LocalDateTime fromDate, LocalDateTime toDate,
                                              String ipAddress) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (businessId != null) predicates.add(cb.equal(root.get("businessId"), businessId));
            if (userId != null) predicates.add(cb.equal(root.get("userId"), userId));
            if (entityType != null && !entityType.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("entityType")), entityType.toLowerCase()));
            }
            if (action != null && !action.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("action")), "%" + action.toLowerCase() + "%"));
            }
            if (category != null && !category.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("category")), category.toLowerCase()));
            }
            if (severity != null && !severity.isBlank()) {
                try { predicates.add(cb.equal(root.get("severity"), AuditLog.AuditSeverity.valueOf(severity.toUpperCase()))); }
                catch (IllegalArgumentException ignored) { }
            }
            if (fromDate != null) predicates.add(cb.greaterThanOrEqualTo(root.get("timestamp"), fromDate));
            if (toDate != null) predicates.add(cb.lessThanOrEqualTo(root.get("timestamp"), toDate));
            if (ipAddress != null && !ipAddress.isBlank()) predicates.add(cb.equal(root.get("ipAddress"), ipAddress));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private String cell(Object value) {
        if (value == null) return "";
        String s = value.toString();
        return s.contains(",") || s.contains("\"") || s.contains("\n")
                ? '"' + s.replace("\"", "\"\"") + '"'
                : s;
    }
}
