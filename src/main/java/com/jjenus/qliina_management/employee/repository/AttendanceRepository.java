package com.jjenus.qliina_management.employee.repository;

import com.jjenus.qliina_management.employee.model.Attendance;
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
public interface AttendanceRepository extends JpaRepository<Attendance, UUID> {
    
    Page<Attendance> findByEmployeeId(UUID employeeId, Pageable pageable);
    
    Page<Attendance> findByShopId(UUID shopId, Pageable pageable);
    
    Optional<Attendance> findByEmployeeIdAndDate(UUID employeeId, LocalDate date);
    
    @Query("SELECT a FROM Attendance a WHERE a.shopId = :shopId AND a.date BETWEEN :startDate AND :endDate")
List<Attendance> findByShopIdAndDateRange(@Param("shopId") UUID shopId,
                                           @Param("startDate") LocalDate startDate,
                                           @Param("endDate") LocalDate endDate);
    
    @Query("SELECT a FROM Attendance a WHERE a.employeeId = :employeeId " +
           "AND a.date BETWEEN :startDate AND :endDate")
    List<Attendance> findByEmployeeIdAndDateRange(@Param("employeeId") UUID employeeId,
                                                   @Param("startDate") LocalDate startDate,
                                                   @Param("endDate") LocalDate endDate);
    
    @Query("SELECT a FROM Attendance a WHERE a.shopId = :shopId " +
           "AND a.date = :date AND a.status = :status")
    List<Attendance> findByShopIdAndDateAndStatus(@Param("shopId") UUID shopId,
                                                    @Param("date") LocalDate date,
                                                    @Param("status") Attendance.AttendanceStatus status);
    
    @Query("SELECT COUNT(a) FROM Attendance a WHERE a.shopId = :shopId " +
           "AND a.date BETWEEN :startDate AND :endDate AND a.status = 'ABSENT'")
    long countAbsencesByShopAndDateRange(@Param("shopId") UUID shopId,
                                          @Param("startDate") LocalDate startDate,
                                          @Param("endDate") LocalDate endDate);
    
    @Query("SELECT a.status, COUNT(a) FROM Attendance a WHERE a.shopId = :shopId " +
           "AND a.date BETWEEN :startDate AND :endDate GROUP BY a.status")
    List<Object[]> getAttendanceSummary(@Param("shopId") UUID shopId,
                                         @Param("startDate") LocalDate startDate,
                                         @Param("endDate") LocalDate endDate);
                                         
}
