package com.jjenus.qliina_management.notification.repository;

import com.jjenus.qliina_management.notification.model.EmailConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmailConfigurationRepository extends JpaRepository<EmailConfiguration, UUID> {
    
    Optional<EmailConfiguration> findByBusinessId(UUID businessId);
}
