package com.jjenus.qliina_management.audit.repository;

import com.jjenus.qliina_management.audit.model.ConsentRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConsentRecordRepository extends JpaRepository<ConsentRecord, UUID> {
    
    List<ConsentRecord> findByCustomerId(UUID customerId);
    
    @Query("SELECT c FROM ConsentRecord c WHERE c.customerId = :customerId AND c.consentType = :type " +
           "AND c.status = 'ACTIVE' ORDER BY c.grantedAt DESC")
    Optional<ConsentRecord> findActiveConsent(@Param("customerId") UUID customerId,
                                               @Param("type") ConsentRecord.ConsentType type);
    
    @Query("SELECT c FROM ConsentRecord c WHERE c.customerId = :customerId AND c.status = 'ACTIVE'")
    List<ConsentRecord> findActiveConsents(@Param("customerId") UUID customerId);
    
    @Query("SELECT c FROM ConsentRecord c WHERE c.expiresAt < :now AND c.status = 'ACTIVE'")
    List<ConsentRecord> findExpiredConsents(@Param("now") LocalDateTime now);
    
    @Query("SELECT c.consentType, COUNT(c) FROM ConsentRecord c WHERE c.businessId = :businessId " +
           "AND c.granted = true GROUP BY c.consentType")
    List<Object[]> getConsentStats(@Param("businessId") UUID businessId);
    
    Page<ConsentRecord> findByBusinessId(UUID businessId, Pageable pageable);
}
