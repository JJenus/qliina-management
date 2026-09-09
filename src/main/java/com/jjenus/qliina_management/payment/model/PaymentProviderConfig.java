package com.jjenus.qliina_management.payment.model;

import com.jjenus.qliina_management.common.BaseTenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * Per-business admin configuration for a payment provider ("either, both, or
 * none", plus a {@link ProviderConnectionMode connection model}). Only explicit
 * admin overrides are stored; a business with no row falls back to the registry
 * defaults (simulator platform-connected on, real processors off until opted in).
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

    /** How this business connects to the processor (default {@code DISCONNECTED}). */
    @Enumerated(EnumType.STRING)
    @Column(name = "connection_mode", length = 16)
    private ProviderConnectionMode connectionMode;

    /** AES-GCM encrypted business-owned credentials (BYO); never exposed. */
    @Column(name = "credentials_encrypted", columnDefinition = "text")
    private String credentialsEncrypted;

    /** Platform subaccount, when this provider routes traffic through Qliina's account. */
    @Column(name = "platform_subaccount_id", length = 64)
    private String platformSubaccountId;
}