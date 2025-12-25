package com.jjenus.qliina_management.quality.model;

import com.jjenus.qliina_management.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "checklist_items")
@Getter
@Setter
public class ChecklistItem extends BaseEntity {
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "checklist_id", nullable = false)
    private QualityChecklist checklist;
    
    @Column(name = "description", nullable = false)
    private String description;
    
    @Column(name = "required")
    private Boolean required = true;
    
    @Column(name = "item_order")
    private Integer itemOrder;
    
    @Column(name = "failure_severity")
    private String failureSeverity;
}