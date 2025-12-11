package com.jjenus.qliina_management.employee.model;

import com.jjenus.qliina_management.common.BaseTenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "employee_performance", indexes = {
    @Index(name = "idx_perf_employee", columnList = "employee_id"),
    @Index(name = "idx_perf_period", columnList = "period_start, period_end")
})
@Getter
@Setter
public class EmployeePerformance extends BaseTenantEntity {
    
    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;
    
    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;
    
    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;
    
    @Column(name = "orders_processed")
    private Integer ordersProcessed = 0;
    
    @Column(name = "items_processed")
    private Integer itemsProcessed = 0;
    
    @Column(name = "revenue_handled", precision = 10, scale = 2)
    private BigDecimal revenueHandled = BigDecimal.ZERO;
    
    @Column(name = "quality_score")
    private Double qualityScore = 0.0;
    
    @Column(name = "attendance_rate")
    private Double attendanceRate = 0.0;
    
    @Column(name = "ontime_rate")
    private Double ontimeRate = 0.0;
    
    @Column(name = "rework_rate")
    private Double reworkRate = 0.0;
    
    @Column(name = "customer_satisfaction")
    private Double customerSatisfaction = 0.0;
    
    @Column(name = "target_achievement")
    private Double targetAchievement = 0.0;
    
    @Column(name = "notes")
    private String notes;
}
