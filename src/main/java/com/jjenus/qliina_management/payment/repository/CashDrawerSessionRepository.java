package com.jjenus.qliina_management.payment.repository;

import com.jjenus.qliina_management.payment.model.CashDrawerSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CashDrawerSessionRepository extends JpaRepository<CashDrawerSession, UUID>, JpaSpecificationExecutor<CashDrawerSession> {
  
    Page<CashDrawerSession> findByShopId(UUID shopId, Pageable pageable);
    
    @Query("SELECT cds FROM CashDrawerSession cds WHERE cds.shopId = :shopId AND cds.status = 'OPEN'")
    Optional<CashDrawerSession> findOpenSession(@Param("shopId") UUID shopId);
}