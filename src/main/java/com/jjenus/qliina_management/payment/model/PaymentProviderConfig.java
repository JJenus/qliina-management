package com.jjenus.qliina_management.payment.model;

import com.jjenus.qliina_management.common.BaseTenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * Per-business admin toggle for a payment provider ("either, both, or none").
 * Only explicit admin overrides are stored; a business with no row falls back
 * to the registry defaults (simulator on, real processors off until opted in).
 */
@Entity
@Table(name = "payment_provider_configs", uniqueConstraints = {
        @UniqueConstraint(name = "uk_payment_provider_configs_business_provider",
                columnNames = {"business_id", "provider"})
})
@Getter
@Setter
public class PaymentProviderConfig extends BaseTenantEntity {

    @Column(name = "provider", nullable = false, length = 32)
    private String provider;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;
}