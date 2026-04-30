package com.jjenus.qliina_management.business.model;

import com.jjenus.qliina_management.common.BaseTenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "garment_types", indexes = {
    @Index(name = "idx_garment_business", columnList = "business_id"),
    @Index(name = "idx_garment_active", columnList = "business_id, is_active")
})
@Getter
@Setter
public class GarmentType extends BaseTenantEntity {

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "category")
    private String category;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "sort_order")
    private Integer sortOrder;

    @Column(name = "icon")
    private String icon;
}