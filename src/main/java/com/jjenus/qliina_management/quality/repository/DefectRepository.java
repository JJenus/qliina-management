package com.jjenus.qliina_management.quality.repository;

import com.jjenus.qliina_management.quality.model.Defect;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface DefectRepository extends JpaRepository<Defect, UUID> {
    
    @Query("SELECT d FROM Defect d WHERE d.qualityCheck.businessId = :businessId AND " +
           "(:status IS NULL OR d.status = :status) AND " +
           "(:severity IS NULL OR d.severity = :severity) AND " +
           "(:fromDate IS NULL OR d.reportedAt >= :fromDate) AND " +
           "(:toDate IS NULL OR d.reportedAt <= :toDate) AND " +
           "(:assignedTo IS NULL OR d.assignedTo = :assignedTo)")
    Page<Defect> findByFilters(@Param("businessId") UUID businessId,
                               @Param("status") String status,
                               @Param("severity") String severity,
                               @Param("fromDate") LocalDateTime fromDate,
                               @Param("toDate") LocalDateTime toDate,
                               @Param("assignedTo") UUID assignedTo,
                               Pageable pageable);
    
    List<Defect> findByReportedByAndReportedAtBetween(UUID reportedBy, 
                                                      LocalDateTime startDate, 
                                                      LocalDateTime endDate);
    
    @Query("SELECT d.type, COUNT(d) FROM Defect d WHERE d.qualityCheck.businessId = :businessId " +
           "AND d.reportedAt BETWEEN :startDate AND :endDate GROUP BY d.type")
    List<Object[]> getDefectTypeDistribution(@Param("businessId") UUID businessId,
                                             @Param("startDate") LocalDateTime startDate,
                                             @Param("endDate") LocalDateTime endDate);
    
    @Query("SELECT d.severity, COUNT(d) FROM Defect d WHERE d.qualityCheck.businessId = :businessId " +
           "AND d.reportedAt BETWEEN :startDate AND :endDate GROUP BY d.severity")
    List<Object[]> getSeverityDistribution(@Param("businessId") UUID businessId,
                                           @Param("startDate") LocalDateTime startDate,
                                           @Param("endDate") LocalDateTime endDate);
}
