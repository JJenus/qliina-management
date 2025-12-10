package com.jjenus.qliina_management.employee.model;

import com.jjenus.qliina_management.common.BaseTenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "employee_shifts", indexes = {
    @Index(name = "idx_shift_employee", columnList = "employee_id"),
    @Index(name = "idx_shift_shop", columnList = "shop_id"),
    @Index(name = "idx_shift_date", columnList = "date"),
    @Index(name = "idx_shift_status", columnList = "status")
})
@Getter
@Setter
public class EmployeeShift extends BaseTenantEntity {
    
    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;
    
    @Column(name = "shop_id", nullable = false)
    private UUID shopId;
    
    @Column(name = "date", nullable = false)
    private LocalDate date;
    
    @Column(name = "scheduled_start", nullable = false)
    private LocalDateTime scheduledStart;
    
    @Column(name = "scheduled_end", nullable = false)
    private LocalDateTime scheduledEnd;
    
    @Column(name = "actual_start")
    private LocalDateTime actualStart;
    
    @Column(name = "actual_end")
    private LocalDateTime actualEnd;
    
    @Column(name = "break_start")
    private LocalDateTime breakStart;
    
    @Column(name = "break_end")
    private LocalDateTime breakEnd;
    
    @Column(name = "total_break_minutes")
    private Integer totalBreakMinutes = 0;
    
    @Column(name = "total_work_minutes")
    private Integer totalWorkMinutes;
    
    @Column(name = "overtime_minutes")
    private Integer overtimeMinutes = 0;
    
    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private ShiftStatus status;
    
    @Column(name = "notes")
    private String notes;
    
    @Column(name = "approved_by")
    private UUID approvedBy;
    
    @Column(name = "approved_at")
    private LocalDateTime approvedAt;
    
    public enum ShiftStatus {
        SCHEDULED, CHECKED_IN, ON_BREAK, CHECKED_OUT, ABSENT, CANCELLED, COMPLETED
    }
    
    public void calculateWorkMinutes() {
        if (actualStart != null && actualEnd != null) {
            long totalMinutes = java.time.Duration.between(actualStart, actualEnd).toMinutes();
            this.totalWorkMinutes = (int) (totalMinutes - totalBreakMinutes);
            
            // Calculate overtime
            long scheduledMinutes = java.time.Duration.between(scheduledStart, scheduledEnd).toMinutes();
            if (totalWorkMinutes > scheduledMinutes) {
                this.overtimeMinutes = (int) (totalWorkMinutes - scheduledMinutes);
            }
        }
    }
    
    public void startBreak() {
        this.breakStart = LocalDateTime.now();
        this.status = ShiftStatus.ON_BREAK;
    }
    
    public void endBreak() {
        this.breakEnd = LocalDateTime.now();
        if (breakStart != null) {
            long breakMinutes = java.time.Duration.between(breakStart, breakEnd).toMinutes();
            this.totalBreakMinutes += (int) breakMinutes;
        }
        this.status = ShiftStatus.CHECKED_IN;
    }
    
    public boolean isActive() {
        return status == ShiftStatus.CHECKED_IN || status == ShiftStatus.ON_BREAK;
    }
}
