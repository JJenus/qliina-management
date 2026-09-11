package com.jjenus.qliina_management.payment.model;

import com.jjenus.qliina_management.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * Platform-level availability of an online payment provider. When
 * {@code platformEnabled} is false, no business can connect the provider or charge
 * through it (fail-closed at checkout); authorizations already initiated keep
 * verifying/refunding. Rows are created lazily on the first platform toggle — a
 * provider with no row is treated as available, matching the pre-platform-control
 * behavior where every registered gateway is visible to businesses.
 */
@Entity
@Table(name = "platform_payment_provider_configs", uniqueConstraints = {
        @UniqueConstraint(name = "uk_platform_payment_provider_configs_provider",
                columnNames = {"provider"})
})
@Getter
@Setter
public class PlatformPaymentProviderConfig extends BaseEntity {

    @Column(name = "provider", nullable = false, length = 32)
    private String provider;

    @Column(name = "platform_enabled", nullable = false)
    private boolean platformEnabled = true;

    @Column(name = "updated_by_username")
    private String updatedByUsername;
}