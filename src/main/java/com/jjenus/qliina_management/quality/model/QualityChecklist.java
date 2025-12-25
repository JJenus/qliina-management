package com.jjenus.qliina_management.quality.model;

import com.jjenus.qliina_management.common.BaseTenantEntity;
import com.jjenus.qliina_management.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.math.BigDecimal;

@Entity
@Table(name = "quality_checklists")
@Getter
@Setter
public class QualityChecklist extends BaseTenantEntity {
    
    @Column(name = "name", nullable = false)
    private String name;
    
    @Column(name = "service_type_id")
    private UUID serviceTypeId;
    
    @Column(name = "garment_type_id")
    private UUID garmentTypeId;
    
    @Column(name = "is_active")
    private Boolean isActive = true;
    
    @OneToMany(mappedBy = "checklist", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("itemOrder ASC")
    private List<ChecklistItem> items = new ArrayList<>();
}

// @Entity
// @Table(name = "checklist_items")
// @Getter
// @Setter
// class ChecklistItem extends BaseEntity {
    
//     @ManyToOne(fetch = FetchType.LAZY)
//     @JoinColumn(name = "checklist_id", nullable = false)
//     private QualityChecklist checklist;
    
//     @Column(name = "description", nullable = false)
//     private String description;
    
//     @Column(name = "required")
//     private Boolean required = true;
    
//     @Column(name = "item_order")
//     private Integer itemOrder;
    
//     @Column(name = "failure_severity")
//     private String failureSeverity;
// }
