package com.jjenus.qliina_management.customer.model;

import com.jjenus.qliina_management.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "loyalty_transactions")
@Getter
@Setter
public class LoyaltyTransaction extends BaseEntity {
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;
    
    @Column(name = "points", nullable = false)
    private Integer points;
    
    @Column(name = "balance", nullable = false)
    private Integer balance;
    
    @Column(name = "source", nullable = false)
    private String source;
    
    @Column(name = "source_id")
    private UUID sourceId;
    
    @Column(name = "description")
    private String description;
}
