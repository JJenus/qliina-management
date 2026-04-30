package com.jjenus.qliina_management.quality.repository;

import com.jjenus.qliina_management.quality.model.Defect;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface DefectRepository extends JpaRepository<Defect, UUID>, JpaSpecificationExecutor<Defect> {
    
    List<Defect> findByReportedByAndReportedAtBetween(
            UUID reportedBy,
            LocalDateTime startDate,
            LocalDateTime endDate);
    
    @Query("SELECT d.type, COUNT(d) FROM Defect d " +
           "WHERE d.qualityCheck.businessId = :businessId " +
           "AND d.reportedAt BETWEEN :startDate AND :endDate " +
           "GROUP BY d.type")
    List<Object[]> getDefectTypeDistribution(
            @Param("businessId") UUID businessId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);
    
    @Query("SELECT d.severity, COUNT(d) FROM Defect d " +
           "WHERE d.qualityCheck.businessId = :businessId " +
           "AND d.reportedAt BETWEEN :startDate AND :endDate " +
           "GROUP BY d.severity")
    List<Object[]> getSeverityDistribution(
            @Param("businessId") UUID businessId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);
}