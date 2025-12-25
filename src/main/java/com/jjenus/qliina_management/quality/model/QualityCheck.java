package com.jjenus.qliina_management.quality.model;

import com.jjenus.qliina_management.common.BaseTenantEntity; 
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "quality_checks")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class QualityCheck extends BaseTenantEntity {
    
    @Column(name = "order_item_id", nullable = false)
    private UUID orderItemId;
    
    @Column(name = "checklist_id")
    private UUID checklistId;
    
    @Column(name = "status")
    private String status;
    
    @Column(name = "checked_by")
    private UUID checkedBy;
    
    @Column(name = "checked_at")
    private LocalDateTime checkedAt;
    
    @Column(name = "notes")
    private String notes;
    
    @OneToMany(mappedBy = "qualityCheck", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<CheckResult> results = new ArrayList<>();
    
    @OneToMany(mappedBy = "qualityCheck", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Defect> defects = new ArrayList<>();
}