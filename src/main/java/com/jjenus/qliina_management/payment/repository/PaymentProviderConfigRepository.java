package com.jjenus.qliina_management.payment.repository;

import com.jjenus.qliina_management.payment.model.PaymentProviderConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentProviderConfigRepository extends JpaRepository<PaymentProviderConfig, UUID> {

    Optional<PaymentProviderConfig> findByBusinessIdAndProvider(UUID businessId, String provider);

    List<PaymentProviderConfig> findByBusinessId(UUID businessId);
}