package com.jjenus.qliina_management.employee.model;

import com.jjenus.qliina_management.common.BaseTenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "employee_schedules", indexes = {
    @Index(name = "idx_schedule_employee", columnList = "employee_id"),
    @Index(name = "idx_schedule_shop", columnList = "shop_id"),
    @Index(name = "idx_schedule_date", columnList = "date")
})
@Getter
@Setter
public class EmployeeSchedule extends BaseTenantEntity {
    
    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;
    
    @Column(name = "shop_id", nullable = false)
    private UUID shopId;
    
    @Column(name = "date")
    private LocalDate date;
    
    @Column(name = "day_of_week")
    @Enumerated(EnumType.STRING)
    private DayOfWeek dayOfWeek;
    
    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;
    
    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;
    
    @Column(name = "role")
    private String role;
    
    @Column(name = "is_recurring")
    private Boolean isRecurring = false;
    
    @Column(name = "recurring_pattern")
    private String recurringPattern; // e.g., "WEEKLY", "BIWEEKLY"
    
    @Column(name = "is_active")
    private Boolean isActive = true;
    
    @Column(name = "notes")
    private String notes;
    
    public LocalDateTime getScheduledStartForDate(LocalDate targetDate) {
        return LocalDateTime.of(targetDate, startTime);
    }
    
    public LocalDateTime getScheduledEndForDate(LocalDate targetDate) {
        return LocalDateTime.of(targetDate, endTime);
    }
}
