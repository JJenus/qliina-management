package com.jjenus.qliina_management.employee.model;

import com.jjenus.qliina_management.common.BaseTenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "attendance", indexes = {
    @Index(name = "idx_attendance_employee", columnList = "employee_id"),
    @Index(name = "idx_attendance_date", columnList = "date"),
    @Index(name = "idx_attendance_status", columnList = "status")
})
@Getter
@Setter
public class Attendance extends BaseTenantEntity {
    
    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;
    
    @Column(name = "date", nullable = false)
    private LocalDate date;
    
    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private AttendanceStatus status;
    
    @Column(name = "check_in_time")
    private LocalDateTime checkInTime;
    
    @Column(name = "check_out_time")
    private LocalDateTime checkOutTime;
    
    @Column(name = "total_hours")
    private Double totalHours;
    
    @Column(name = "overtime_hours")
    private Double overtimeHours;
    
    @Column(name = "notes")
    private String notes;
    
    @Column(name = "approved_by")
    private UUID approvedBy;
    
    @Column(name = "approved_at")
    private LocalDateTime approvedAt;
    
    public enum AttendanceStatus {
        PRESENT, ABSENT, LATE, HALF_DAY, HOLIDAY, LEAVE, SICK
    }
}
