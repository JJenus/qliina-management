package com.jjenus.qliina_management.identity.repository;

import com.jjenus.qliina_management.identity.model.BusinessConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface BusinessConfigRepository extends JpaRepository<BusinessConfig, UUID> {
    Optional<BusinessConfig> findByBusinessId(UUID businessId);
}