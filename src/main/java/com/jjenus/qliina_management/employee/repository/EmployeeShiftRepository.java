package com.jjenus.qliina_management.employee.repository;

import com.jjenus.qliina_management.employee.model.EmployeeShift;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmployeeShiftRepository extends JpaRepository<EmployeeShift, UUID> {
    
    Page<EmployeeShift> findByEmployeeId(UUID employeeId, Pageable pageable);
    
    Page<EmployeeShift> findByShopId(UUID shopId, Pageable pageable);
    
    @Query("SELECT COUNT(DISTINCT es.employeeId) FROM EmployeeShift es WHERE es.shopId = :shopId AND es.date = CURRENT_DATE AND es.status IN ('CHECKED_IN', 'ON_BREAK')")
Long countActiveEmployees(@Param("shopId") UUID shopId);
    
    @Query("SELECT es FROM EmployeeShift es WHERE es.employeeId = :employeeId AND es.date = :date")
    Optional<EmployeeShift> findByEmployeeIdAndDate(@Param("employeeId") UUID employeeId,
                                                     @Param("date") LocalDate date);
    
    @Query("SELECT es FROM EmployeeShift es WHERE es.employeeId = :employeeId " +
           "AND es.status IN ('CHECKED_IN', 'ON_BREAK')")
    Optional<EmployeeShift> findActiveShift(@Param("employeeId") UUID employeeId);
    
    @Query("SELECT es FROM EmployeeShift es WHERE es.shopId = :shopId AND es.date = :date")
    List<EmployeeShift> findByShopIdAndDate(@Param("shopId") UUID shopId,
                                             @Param("date") LocalDate date);
    
    @Query("SELECT es FROM EmployeeShift es WHERE es.shopId = :shopId " +
           "AND es.date BETWEEN :startDate AND :endDate")
    List<EmployeeShift> findByShopIdAndDateRange(@Param("shopId") UUID shopId,
                                                  @Param("startDate") LocalDate startDate,
                                                  @Param("endDate") LocalDate endDate);
    
    @Query("SELECT es FROM EmployeeShift es WHERE es.employeeId = :employeeId " +
           "AND es.date BETWEEN :startDate AND :endDate")
    List<EmployeeShift> findByEmployeeIdAndDateRange(@Param("employeeId") UUID employeeId,
                                                      @Param("startDate") LocalDate startDate,
                                                      @Param("endDate") LocalDate endDate);
    
    @Query("SELECT COUNT(es) FROM EmployeeShift es WHERE es.shopId = :shopId " +
           "AND es.date = :date AND es.status = 'ABSENT'")
    long countAbsentByShopAndDate(@Param("shopId") UUID shopId,
                                   @Param("date") LocalDate date);
    
    @Query("SELECT AVG(es.overtimeMinutes) FROM EmployeeShift es " +
           "WHERE es.shopId = :shopId AND es.date BETWEEN :startDate AND :endDate")
    Double averageOvertimeByShopAndDateRange(@Param("shopId") UUID shopId,
                                              @Param("startDate") LocalDate startDate,
                                              @Param("endDate") LocalDate endDate);
}
