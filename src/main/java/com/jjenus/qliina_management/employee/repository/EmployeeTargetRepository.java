package com.jjenus.qliina_management.employee.repository;

import com.jjenus.qliina_management.employee.model.EmployeeTarget;
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
public interface EmployeeTargetRepository extends JpaRepository<EmployeeTarget, UUID> {
    
    Page<EmployeeTarget> findByEmployeeId(UUID employeeId, Pageable pageable);
    
    @Query("SELECT et FROM EmployeeTarget et WHERE et.employeeId = :employeeId " +
           "AND et.date = :date AND et.metric = :metric")
    Optional<EmployeeTarget> findByEmployeeIdAndDateAndMetric(@Param("employeeId") UUID employeeId,
                                                                @Param("date") LocalDate date,
                                                                @Param("metric") EmployeeTarget.TargetMetric metric);
    
    @Query("SELECT et FROM EmployeeTarget et WHERE et.employeeId = :employeeId " +
           "AND et.date BETWEEN :startDate AND :endDate")
    List<EmployeeTarget> findByEmployeeIdAndDateRange(@Param("employeeId") UUID employeeId,
                                                       @Param("startDate") LocalDate startDate,
                                                       @Param("endDate") LocalDate endDate);

    @Query("""
       SELECT AVG(CASE WHEN et.achieved = true THEN 1.0 ELSE 0.0 END)
       FROM EmployeeTarget et
       WHERE et.employeeId = :employeeId
       AND et.date BETWEEN :startDate AND :endDate
       """)
    Double averageAchievementRate(@Param("employeeId") UUID employeeId,
                                  @Param("startDate") LocalDate startDate,
                                  @Param("endDate") LocalDate endDate);
    
    @Query("SELECT et.metric, AVG(CASE WHEN et.achieved = true THEN 1.0 ELSE 0.0 END) FROM EmployeeTarget et " +
           "WHERE et.employeeId = :employeeId AND et.date BETWEEN :startDate AND :endDate " +
           "GROUP BY et.metric")
    List<Object[]> achievementByMetric(@Param("employeeId") UUID employeeId,
                                        @Param("startDate") LocalDate startDate,
                                        @Param("endDate") LocalDate endDate);
}
