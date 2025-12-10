package com.jjenus.qliina_management.employee.repository;

import com.jjenus.qliina_management.employee.model.EmployeePerformance;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmployeePerformanceRepository extends JpaRepository<EmployeePerformance, UUID> {
    
    Page<EmployeePerformance> findByEmployeeId(UUID employeeId, Pageable pageable);
    
    @Query("SELECT ep FROM EmployeePerformance ep WHERE ep.employeeId = :employeeId " +
           "AND ep.periodStart = :startDate AND ep.periodEnd = :endDate")
    Optional<EmployeePerformance> findByEmployeeIdAndPeriod(@Param("employeeId") UUID employeeId,
                                                             @Param("startDate") LocalDate startDate,
                                                             @Param("endDate") LocalDate endDate);
    
    @Query("SELECT ep FROM EmployeePerformance ep WHERE ep.employeeId = :employeeId " +
           "ORDER BY ep.periodEnd DESC")
    List<EmployeePerformance> findLatestByEmployeeId(@Param("employeeId") UUID employeeId,
                                                      Pageable pageable);
    
    @Query("SELECT ep FROM EmployeePerformance ep WHERE ep.periodEnd >= :startDate")
    List<EmployeePerformance> findRecentPerformance(@Param("startDate") LocalDate startDate);
    
    @Query("SELECT AVG(ep.qualityScore) FROM EmployeePerformance ep " +
           "WHERE ep.shopId = :shopId AND ep.periodEnd BETWEEN :startDate AND :endDate")
    Double averageQualityScoreByShop(@Param("shopId") UUID shopId,
                                      @Param("startDate") LocalDate startDate,
                                      @Param("endDate") LocalDate endDate);
    
    @Query("SELECT ep.employeeId, AVG(ep.qualityScore) as avgQuality " +
           "FROM EmployeePerformance ep WHERE ep.businessId = :businessId " +
           "AND ep.periodEnd BETWEEN :startDate AND :endDate " +
           "GROUP BY ep.employeeId ORDER BY avgQuality DESC")
    List<Object[]> getEmployeeRankings(@Param("businessId") UUID businessId,
                                        @Param("startDate") LocalDate startDate,
                                        @Param("endDate") LocalDate endDate,
                                        Pageable pageable);
}
