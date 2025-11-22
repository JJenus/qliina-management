package com.jjenus.qliina_management.audit.model;

import com.jjenus.qliina_management.common.BaseTenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "compliance_reports", indexes = {
    @Index(name = "idx_compliance_business", columnList = "business_id"),
    @Index(name = "idx_compliance_period", columnList = "period_start, period_end"),
    @Index(name = "idx_compliance_type", columnList = "report_type")
})
@Getter
@Setter
public class ComplianceReport extends BaseTenantEntity {
    
    @Column(name = "report_number", nullable = false, unique = true)
    private String reportNumber;
    
    @Column(name = "report_type", nullable = false)
    private String reportType;
    
    @Column(name = "period_start")
    private LocalDateTime periodStart;
    
    @Column(name = "period_end")
    private LocalDateTime periodEnd;
    
    @Column(name = "generated_at", nullable = false)
    private LocalDateTime generatedAt;
    
    @Column(name = "generated_by")
    private UUID generatedBy;
    
    @Column(name = "report_data", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String reportData;
    
    @Column(name = "file_url")
    private String fileUrl;
    
    @Column(name = "file_size")
    private Long fileSize;
    
    @Column(name = "status")
    private String status;
    
    @Column(name = "notes")
    private String notes;
}
