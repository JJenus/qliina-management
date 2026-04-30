package com.jjenus.qliina_management.business.model;

import com.jjenus.qliina_management.common.BaseTenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "service_garment_pricing", indexes = {
    @Index(name = "idx_pricing_business", columnList = "business_id"),
    @Index(name = "idx_pricing_service", columnList = "service_type_id"),
    @Index(name = "idx_pricing_garment", columnList = "garment_type_id"),
    @Index(name = "idx_pricing_combo", columnList = "service_type_id, garment_type_id", unique = true)
})
@Getter
@Setter
public class ServiceGarmentPricing extends BaseTenantEntity {

    @Column(name = "service_type_id", nullable = false)
    private UUID serviceTypeId;

    @Column(name = "garment_type_id", nullable = false)
    private UUID garmentTypeId;

    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "is_active")
    private Boolean isActive = true;
}