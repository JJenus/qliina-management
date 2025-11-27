package com.jjenus.qliina_management.customer.model;

import com.jjenus.qliina_management.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "customer_notes")
@Getter
@Setter
public class CustomerNote extends BaseEntity {
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;
    
    @Column(name = "content", nullable = false, length = 2000)
    private String content;
    
    @Column(name = "type")
    private String type;
}
