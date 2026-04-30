package com.jjenus.qliina_management.business.model;

import com.jjenus.qliina_management.common.BaseTenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "service_types", indexes = {
    @Index(name = "idx_svc_type_business", columnList = "business_id"),
    @Index(name = "idx_svc_type_active", columnList = "business_id, is_active")
})
@Getter
@Setter
public class ServiceType extends BaseTenantEntity {

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "category")
    private String category;

    @Column(name = "default_price", precision = 10, scale = 2)
    private BigDecimal defaultPrice;

    @Column(name = "unit")
    private String unit = "PER_ITEM";

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "sort_order")
    private Integer sortOrder;

    @Column(name = "icon")
    private String icon;
}