package com.jjenus.qliina_management.payment.repository;

import com.jjenus.qliina_management.payment.model.PlatformPaymentProviderConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PlatformPaymentProviderConfigRepository
        extends JpaRepository<PlatformPaymentProviderConfig, UUID> {

    Optional<PlatformPaymentProviderConfig> findByProvider(String provider);
}