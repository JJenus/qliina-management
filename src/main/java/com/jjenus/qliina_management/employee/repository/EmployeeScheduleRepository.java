package com.jjenus.qliina_management.employee.repository;

import com.jjenus.qliina_management.employee.model.EmployeeSchedule;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface EmployeeScheduleRepository extends JpaRepository<EmployeeSchedule, UUID> {
    
    Page<EmployeeSchedule> findByEmployeeId(UUID employeeId, Pageable pageable);
    
    Page<EmployeeSchedule> findByShopId(UUID shopId, Pageable pageable);
    
    @Query("SELECT es FROM EmployeeSchedule es WHERE es.employeeId = :employeeId " +
           "AND es.date BETWEEN :startDate AND :endDate")
    List<EmployeeSchedule> findByEmployeeIdAndDateRange(@Param("employeeId") UUID employeeId,
                                                         @Param("startDate") LocalDate startDate,
                                                         @Param("endDate") LocalDate endDate);
    
    @Query("SELECT es FROM EmployeeSchedule es WHERE es.shopId = :shopId AND es.date = :date")
    List<EmployeeSchedule> findByShopIdAndDate(@Param("shopId") UUID shopId,
                                                @Param("date") LocalDate date);
    
    @Query("SELECT es FROM EmployeeSchedule es WHERE es.shopId = :shopId " +
           "AND es.date BETWEEN :startDate AND :endDate")
    List<EmployeeSchedule> findByShopIdAndDateRange(@Param("shopId") UUID shopId,
                                                     @Param("startDate") LocalDate startDate,
                                                     @Param("endDate") LocalDate endDate);
    
    @Query("SELECT es FROM EmployeeSchedule es WHERE es.employeeId = :employeeId " +
           "AND es.isRecurring = true AND es.isActive = true")
    List<EmployeeSchedule> findRecurringSchedules(@Param("employeeId") UUID employeeId);
    
    @Query("SELECT es FROM EmployeeSchedule es WHERE es.shopId = :shopId " +
           "AND es.dayOfWeek = :dayOfWeek AND es.isRecurring = true AND es.isActive = true")
    List<EmployeeSchedule> findRecurringSchedulesByDay(@Param("shopId") UUID shopId,
                                                        @Param("dayOfWeek") DayOfWeek dayOfWeek);
}
