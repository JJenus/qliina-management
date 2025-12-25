// src/main/java/com/jjenus/qliina_management/quality/model/CheckResult.java
package com.jjenus.qliina_management.quality.model;

import com.jjenus.qliina_management.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "check_results")
@Getter
@Setter
public class CheckResult extends BaseEntity {
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quality_check_id", nullable = false)
    private QualityCheck qualityCheck;
    
    @Column(name = "checklist_item_id")
    private UUID checklistItemId;
    
    @Column(name = "passed")
    private Boolean passed;
    
    @Column(name = "notes")
    private String notes;
}