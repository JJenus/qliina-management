package com.jjenus.qliina_management.audit.model;

import com.jjenus.qliina_management.common.BaseTenantEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "data_retention_policies", indexes = {
    @Index(name = "idx_retention_entity", columnList = "entity_type"),
    @Index(name = "idx_retention_business", columnList = "business_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DataRetentionPolicy extends BaseTenantEntity {
    
    @Column(name = "entity_type", nullable = false)
    private String entityType;
    
    @Column(name = "retention_days", nullable = false)
    private Integer retentionDays;
    
    @Column(name = "archive_enabled")
    private Boolean archiveEnabled = false;
    
    @Column(name = "archive_location")
    private String archiveLocation;
    
    @Column(name = "delete_enabled")
    private Boolean deleteEnabled = true;
    
    @Column(name = "notification_days_before")
    private Integer notificationDaysBefore;
    
    @Column(name = "last_run")
    private LocalDateTime lastRun;
    
    @Column(name = "next_run")
    private LocalDateTime nextRun;
    
    @Column(name = "is_active")
    private Boolean isActive = true;
}
