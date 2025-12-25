package com.jjenus.qliina_management.quality.repository;

import com.jjenus.qliina_management.quality.model.QualityCheck;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.Optional;

@Repository
public interface QualityCheckRepository extends JpaRepository<QualityCheck, UUID> {

    Page<QualityCheck> findByOrderItemId(UUID orderItemId, Pageable pageable);

    Optional<QualityCheck> findByOrderItemId(UUID orderItemId);

    @Query("SELECT AVG(CASE WHEN qc.status = 'PASSED' THEN 100 ELSE 0 END) " +
            "FROM QualityCheck qc WHERE qc.checkedBy = :employeeId AND FUNCTION('DATE', qc.checkedAt) = :date")
    Double averageScoreByEmployeeIdAndDate(@Param("employeeId") UUID employeeId,
                                           @Param("date") LocalDate date);

    @Query("SELECT AVG(CASE WHEN qc.status = 'PASSED' THEN 100 ELSE 0 END) " +
            "FROM QualityCheck qc WHERE qc.checkedBy = :employeeId AND qc.checkedAt BETWEEN :startDate AND :endDate")
    Double averageScoreByEmployeeIdAndDateRange(@Param("employeeId") UUID employeeId,
                                                @Param("startDate") LocalDateTime startDate,
                                                @Param("endDate") LocalDateTime endDate);

    @Query("SELECT COUNT(CASE WHEN qc.status = 'FAILED' THEN 1 END) * 100.0 / COUNT(qc) " +
            "FROM QualityCheck qc WHERE qc.checkedBy = :employeeId AND qc.checkedAt BETWEEN :startDate AND :endDate")
    Double reworkRateByEmployeeIdAndDateRange(@Param("employeeId") UUID employeeId,
                                              @Param("startDate") LocalDateTime startDate,
                                              @Param("endDate") LocalDateTime endDate);

    @Query("""
    SELECT qc.checkedBy, 
           CONCAT(u.firstName, ' ', u.lastName), 
           COUNT(qc), 
           SUM(CASE WHEN qc.status = 'PASSED' THEN 1 ELSE 0 END), 
           SUM(CASE WHEN qc.status = 'FAILED' THEN 1 ELSE 0 END)
    FROM QualityCheck qc 
    JOIN User u ON qc.checkedBy = u.id 
    WHERE qc.businessId = :businessId 
      AND qc.checkedAt BETWEEN :startDate AND :endDate 
    GROUP BY qc.checkedBy, u.firstName, u.lastName
""")
List<Object[]> getEmployeePerformance(@Param("businessId") UUID businessId,
                                      @Param("startDate") LocalDateTime startDate,
                                      @Param("endDate") LocalDateTime endDate);
 
    @Query("SELECT FUNCTION('DATE', qc.checkedAt), " +
            "AVG(CASE WHEN qc.status = 'PASSED' THEN 100 ELSE 0 END) " +
            "FROM QualityCheck qc WHERE qc.checkedBy = :employeeId " +
            "AND qc.checkedAt BETWEEN :startDate AND :endDate " +
            "GROUP BY FUNCTION('DATE', qc.checkedAt)")
    List<Object[]> getDailyPerformance(@Param("employeeId") UUID employeeId,
                                       @Param("startDate") LocalDateTime startDate,
                                       @Param("endDate") LocalDateTime endDate);

    @Query("SELECT qc.checkedBy, " +
       "SUM(CASE WHEN qc.status = 'PASSED' THEN 1 ELSE 0 END) * 100.0 / COUNT(qc) as passRate " +
       "FROM QualityCheck qc WHERE qc.businessId = :businessId " +
       "AND qc.checkedAt BETWEEN :startDate AND :endDate " +
       "GROUP BY qc.checkedBy ORDER BY passRate DESC")
List<Object[]> getEmployeeRankings(@Param("businessId") UUID businessId,
                                   @Param("startDate") LocalDateTime startDate,
                                   @Param("endDate") LocalDateTime endDate);

    @Query("SELECT COUNT(qc) FROM QualityCheck qc WHERE qc.checkedBy = :employeeId " +
            "AND qc.checkedAt BETWEEN :startDate AND :endDate")
    Long countItemsProcessedByEmployee(@Param("employeeId") UUID employeeId,
                                       @Param("startDate") LocalDateTime startDate,
                                       @Param("endDate") LocalDateTime endDate);

    @Query("SELECT COUNT(qc) FROM QualityCheck qc WHERE qc.checkedBy = :employeeId " +
            "AND qc.status = 'PASSED' AND qc.checkedAt BETWEEN :startDate AND :endDate")
    Long countItemsPassedByEmployee(@Param("employeeId") UUID employeeId,
                                    @Param("startDate") LocalDateTime startDate,
                                    @Param("endDate") LocalDateTime endDate);

    @Query("SELECT COUNT(qc) FROM QualityCheck qc WHERE qc.checkedBy = :employeeId " +
            "AND qc.status = 'FAILED' AND qc.checkedAt BETWEEN :startDate AND :endDate")
    Long countItemsFailedByEmployee(@Param("employeeId") UUID employeeId,
                                    @Param("startDate") LocalDateTime startDate,
                                    @Param("endDate") LocalDateTime endDate);
}