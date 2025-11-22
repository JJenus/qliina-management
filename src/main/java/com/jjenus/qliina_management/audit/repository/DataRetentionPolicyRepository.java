package com.jjenus.qliina_management.audit.repository;

import com.jjenus.qliina_management.audit.model.DataRetentionPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DataRetentionPolicyRepository extends JpaRepository<DataRetentionPolicy, UUID> {
    
    Optional<DataRetentionPolicy> findByBusinessIdAndEntityType(UUID businessId, String entityType);
    
    List<DataRetentionPolicy> findByBusinessId(UUID businessId);
    
    @Query("SELECT p FROM DataRetentionPolicy p WHERE p.isActive = true AND p.nextRun <= :now")
    List<DataRetentionPolicy> findPoliciesDueForExecution(@Param("now") LocalDateTime now);
    
    @Query("SELECT p FROM DataRetentionPolicy p WHERE p.businessId = :businessId AND p.isActive = true")
    List<DataRetentionPolicy> findActivePolicies(@Param("businessId") UUID businessId);
}
