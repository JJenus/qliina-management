package com.jjenus.qliina_management.employee.repository;

import com.jjenus.qliina_management.employee.model.TimeEntry;
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
public interface TimeEntryRepository extends JpaRepository<TimeEntry, UUID> {
    
    Page<TimeEntry> findByEmployeeId(UUID employeeId, Pageable pageable);
    
    Page<TimeEntry> findByShopId(UUID shopId, Pageable pageable);
    
    @Query("SELECT te FROM TimeEntry te WHERE te.employeeId = :employeeId " +
           "AND te.timestamp BETWEEN :startDate AND :endDate")
    List<TimeEntry> findByEmployeeIdAndDateRange(@Param("employeeId") UUID employeeId,
                                                  @Param("startDate") LocalDateTime startDate,
                                                  @Param("endDate") LocalDateTime endDate);
    
    @Query("SELECT te FROM TimeEntry te WHERE te.shiftId = :shiftId ORDER BY te.timestamp")
    List<TimeEntry> findByShiftId(@Param("shiftId") UUID shiftId);
    
    @Query("SELECT te FROM TimeEntry te WHERE te.employeeId = :employeeId " +
           "AND te.eventType = :eventType AND te.timestamp >= :since")
    List<TimeEntry> findRecentEvents(@Param("employeeId") UUID employeeId,
                                      @Param("eventType") TimeEntry.EventType eventType,
                                      @Param("since") LocalDateTime since);
}
