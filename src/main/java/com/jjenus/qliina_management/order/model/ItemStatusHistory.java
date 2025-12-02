package com.jjenus.qliina_management.order.model;

import com.jjenus.qliina_management.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "item_status_history")
@Getter
@Setter
public class ItemStatusHistory extends BaseEntity {
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private OrderItem orderItem;
    
    @Column(name = "status", nullable = false)
    private String status;
    
    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;
    
    @Column(name = "updated_by")
    private UUID updatedBy;
    
    @Column(name = "notes")
    private String notes;
}
