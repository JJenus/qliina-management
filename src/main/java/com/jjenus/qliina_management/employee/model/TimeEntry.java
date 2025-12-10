package com.jjenus.qliina_management.employee.model;

import com.jjenus.qliina_management.common.BaseTenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "time_entries", indexes = {
    @Index(name = "idx_time_employee", columnList = "employee_id"),
    @Index(name = "idx_time_shop", columnList = "shop_id"),
    @Index(name = "idx_time_timestamp", columnList = "timestamp")
})
@Getter
@Setter
public class TimeEntry extends BaseTenantEntity {
    
    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;
    
    @Column(name = "shop_id", nullable = false)
    private UUID shopId;
    
    @Column(name = "event_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private EventType eventType;
    
    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;
    
    @Column(name = "device_id")
    private String deviceId;
    
    @Column(name = "ip_address")
    private String ipAddress;
    
    @Column(name = "location")
    private String location;
    
    @Column(name = "notes")
    private String notes;
    
    @Column(name = "shift_id")
    private UUID shiftId;
    
    public enum EventType {
        CLOCK_IN, CLOCK_OUT, BREAK_START, BREAK_END
    }
}
