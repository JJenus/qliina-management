package com.jjenus.qliina_management.order.model;

import com.jjenus.qliina_management.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "order_notes")
@Getter
@Setter
public class OrderNote extends BaseEntity {
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;
    
    @Column(name = "content", nullable = false, length = 2000)
    private String content;
    
    @Column(name = "type")
    private String type;
    
    @Column(name = "is_customer_visible")
    private Boolean isCustomerVisible = false;
}
