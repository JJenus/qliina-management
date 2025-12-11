package com.jjenus.qliina_management.employee.model;

import com.jjenus.qliina_management.common.BaseTenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "employee_targets", indexes = {
    @Index(name = "idx_target_employee", columnList = "employee_id"),
    @Index(name = "idx_target_date", columnList = "date"),
    @Index(name = "idx_target_metric", columnList = "metric")
})
@Getter
@Setter
public class EmployeeTarget extends BaseTenantEntity {
    
    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;
    
    @Column(name = "date", nullable = false)
    private LocalDate date;
    
    @Column(name = "metric", nullable = false)
    @Enumerated(EnumType.STRING)
    private TargetMetric metric;
    
    @Column(name = "target_value", nullable = false)
    private Integer targetValue;
    
    @Column(name = "actual_value")
    private Integer actualValue;
    
    @Column(name = "achieved")
    private Boolean achieved;
    
    @Column(name = "notes")
    private String notes;
    
    public enum TargetMetric {
        ORDERS, ITEMS, REVENUE, QUALITY, CUSTOMER_SATISFACTION
    }
    
    public void updateAchieved() {
        if (actualValue != null) {
            this.achieved = actualValue >= targetValue;
        }
    }
}
