package com.jjenus.qliina_management.payment.repository;

import com.jjenus.qliina_management.payment.model.CashDrawerSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CashDrawerSessionRepository extends JpaRepository<CashDrawerSession, UUID> {
    
    @Query("SELECT cds FROM CashDrawerSession cds WHERE cds.shopId = :shopId AND cds.status = 'OPEN'")
    Optional<CashDrawerSession> findOpenSession(@Param("shopId") UUID shopId);
    
    Page<CashDrawerSession> findByShopId(UUID shopId, Pageable pageable);
    
    @Query("SELECT cds FROM CashDrawerSession cds WHERE cds.businessId = :businessId " +
           "AND (:shopId IS NULL OR cds.shopId = :shopId) " +
           "AND (:status IS NULL OR cds.status = :status) " +
           "AND (:fromDate IS NULL OR cds.openedAt >= :fromDate) " +
           "AND (:toDate IS NULL OR cds.openedAt <= :toDate)")
    Page<CashDrawerSession> findByFilters(@Param("businessId") UUID businessId,
                                          @Param("shopId") UUID shopId,
                                          @Param("status") String status,
                                          @Param("fromDate") LocalDateTime fromDate,
                                          @Param("toDate") LocalDateTime toDate,
                                          Pageable pageable);
}
