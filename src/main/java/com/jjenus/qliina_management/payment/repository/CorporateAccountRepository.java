package com.jjenus.qliina_management.payment.repository;

import com.jjenus.qliina_management.payment.model.CorporateAccount;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CorporateAccountRepository extends JpaRepository<CorporateAccount, UUID> {
    
    Page<CorporateAccount> findByBusinessId(UUID businessId, Pageable pageable);
    
    Optional<CorporateAccount> findByCustomerId(UUID customerId);
    
    List<CorporateAccount> findByStatus(String status);
    
    @Query("SELECT ca FROM CorporateAccount ca WHERE ca.businessId = :businessId " +
           "AND (:status IS NULL OR ca.status = :status)")
    Page<CorporateAccount> findByBusinessIdAndStatus(@Param("businessId") UUID businessId,
                                                     @Param("status") String status,
                                                     Pageable pageable);
}
