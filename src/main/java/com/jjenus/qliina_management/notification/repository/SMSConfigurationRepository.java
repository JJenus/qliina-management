package com.jjenus.qliina_management.notification.repository;

import com.jjenus.qliina_management.notification.model.SMSConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SMSConfigurationRepository extends JpaRepository<SMSConfiguration, UUID> {
    
    Optional<SMSConfiguration> findByBusinessId(UUID businessId);
}
