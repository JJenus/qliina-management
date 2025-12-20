package com.jjenus.qliina_management.notification.repository;

import com.jjenus.qliina_management.notification.model.PushNotificationConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PushNotificationConfigurationRepository extends JpaRepository<PushNotificationConfiguration, UUID> {
    
    Optional<PushNotificationConfiguration> findByBusinessId(UUID businessId);
}
