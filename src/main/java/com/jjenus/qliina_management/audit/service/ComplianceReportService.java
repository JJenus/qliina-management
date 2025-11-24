package com.jjenus.qliina_management.audit.service;

import com.jjenus.qliina_management.audit.dto.ComplianceReportDTO;
import com.jjenus.qliina_management.audit.model.ComplianceReport;
import com.jjenus.qliina_management.audit.repository.ComplianceReportRepository;
import com.jjenus.qliina_management.common.BusinessException;
import com.jjenus.qliina_management.common.PageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ComplianceReportService {
    
    private final ComplianceReportRepository reportRepository;
    private final AuditService auditService;
    private final ConsentService consentService;
    private final DataSubjectRequestService dsrService;
    
    @Transactional
    public ComplianceReportDTO generateGDPRReport(UUID businessId) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startDate = now.minusMonths(3); // Last 3 months
        
        // Gather compliance data
        var auditSummary = auditService.getAuditSummary(businessId, startDate, now);
        
        // Get consent statistics
        var consentStats = consentService.getConsentStats(businessId);
        
        // Get DSR statistics
        long pendingDSR = dsrService.countRequestsByStatus(businessId, "SUBMITTED");
        long completedDSR = dsrService.countRequestsByStatus(businessId, "COMPLETED");
        
        ComplianceReport report = new ComplianceReport();
        report.setBusinessId(businessId);
        report.setReportNumber(generateReportNumber(businessId, "GDPR"));
        report.setReportType("GDPR_COMPLIANCE");
        report.setPeriodStart(startDate);
        report.setPeriodEnd(now);
        report.setGeneratedAt(now);
        report.setGeneratedBy(getCurrentUserId());
        report.setStatus("GENERATED");
        
        // Build report data
        var reportData = Map.of(
            "auditSummary", auditSummary,
            "consentStats", consentStats,
            "dsrStats", Map.of(
                "pending", pendingDSR,
                "completed", completedDSR
            )
        );
        
        report.setReportData(reportData.toString()); // In real impl, use JSON
        
        report = reportRepository.save(report);
        
        return mapToDTO(report);
    }
    
    @Transactional
    public ComplianceReportDTO generateSOXReport(UUID businessId) {
        // Implementation for SOX compliance report
        return null;
    }
    
    @Transactional(readOnly = true)
    public PageResponse<ComplianceReportDTO> listReports(UUID businessId, String reportType, 
                                                           Pageable pageable) {
        Page<ComplianceReport> page;
        if (reportType != null) {
            page = reportRepository.findByBusinessIdAndReportType(businessId, reportType, pageable);
        } else {
            page = reportRepository.findByBusinessId(businessId, pageable);
        }
        return PageResponse.from(page.map(this::mapToDTO));
    }
    
    @Transactional(readOnly = true)
    public ComplianceReportDTO getReport(UUID reportId) {
        ComplianceReport report = reportRepository.findById(reportId)
            .orElseThrow(() -> new BusinessException("Report not found", "REPORT_NOT_FOUND"));
        return mapToDTO(report);
    }
    
    @Transactional
    public byte[] downloadReport(UUID reportId) {
        ComplianceReport report = reportRepository.findById(reportId)
            .orElseThrow(() -> new BusinessException("Report not found", "REPORT_NOT_FOUND"));
        
        // In a real implementation, generate PDF/CSV file
        return new byte[0];
    }
    
    private String generateReportNumber(UUID businessId, String prefix) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        return String.format("%s-%s-%s", prefix, businessId.toString().substring(0, 4), timestamp);
    }
    
    private UUID getCurrentUserId() {
        // In a real implementation, get from SecurityContext
        return UUID.randomUUID();
    }
    
    private ComplianceReportDTO mapToDTO(ComplianceReport report) {
        return ComplianceReportDTO.builder()
            .id(report.getId())
            .reportNumber(report.getReportNumber())
            .reportType(report.getReportType())
            .periodStart(report.getPeriodStart())
            .periodEnd(report.getPeriodEnd())
            .generatedAt(report.getGeneratedAt())
            .generatedBy(report.getGeneratedBy() != null ? report.getGeneratedBy().toString() : null)
            .reportData(report.getReportData())
            .fileUrl(report.getFileUrl())
            .fileSize(report.getFileSize())
            .status(report.getStatus())
            .build();
    }
}
