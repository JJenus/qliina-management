package com.jjenus.qliina_management.common;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@MappedSuperclass
public abstract class BaseTenantEntity extends BaseEntity {
    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "shop_id")
    private UUID shopId;
}
