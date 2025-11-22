package com.jjenus.qliina_management.audit.repository;

import com.jjenus.qliina_management.audit.model.ComplianceReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ComplianceReportRepository extends JpaRepository<ComplianceReport, UUID> {
    
    Optional<ComplianceReport> findByReportNumber(String reportNumber);
    
    Page<ComplianceReport> findByBusinessIdAndReportType(UUID businessId, String reportType, Pageable pageable);
    
    Page<ComplianceReport> findByBusinessId(UUID businessId, Pageable pageable);
    
    @Query("SELECT c FROM ComplianceReport c WHERE c.businessId = :businessId " +
           "AND c.reportType = :reportType " +
           "AND c.periodStart >= :startDate AND c.periodEnd <= :endDate")
    List<ComplianceReport> findReportsByTypeAndPeriod(@Param("businessId") UUID businessId,
                                                       @Param("reportType") String reportType,
                                                       @Param("startDate") LocalDateTime startDate,
                                                       @Param("endDate") LocalDateTime endDate);
    
    @Query("SELECT c FROM ComplianceReport c WHERE c.generatedAt >= :since")
    List<ComplianceReport> findRecentReports(@Param("since") LocalDateTime since);
}
