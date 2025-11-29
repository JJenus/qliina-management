package com.jjenus.qliina_management.customer.model;

import com.jjenus.qliina_management.common.BaseTenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "loyalty_tiers")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoyaltyTier extends BaseTenantEntity {
    
    @Column(name = "name", nullable = false)
    private String name;
    
    @Column(name = "level", nullable = false)
    private Integer level;
    
    @Column(name = "points_required", nullable = false)
    private Integer pointsRequired;
    
    @Column(name = "benefits", columnDefinition = "text[]")
    private List<String> benefits = new ArrayList<>();
}
