package com.jjenus.qliina_management.customer.model;

import com.jjenus.qliina_management.common.BaseTenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "loyalty_rewards")
@Getter
@Setter
public class LoyaltyReward extends BaseTenantEntity {
    
    @Column(name = "name", nullable = false)
    private String name;
    
    @Column(name = "description")
    private String description;
    
    @Column(name = "points_cost", nullable = false)
    private Integer pointsCost;
    
    @Column(name = "is_active")
    private Boolean isActive = true;
    
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;
}
