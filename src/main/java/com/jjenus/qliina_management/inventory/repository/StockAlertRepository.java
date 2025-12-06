package com.jjenus.qliina_management.inventory.repository;

import com.jjenus.qliina_management.inventory.model.StockAlert;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;

import java.util.List;
import java.util.UUID;

@Repository
public interface StockAlertRepository extends JpaRepository<StockAlert, UUID> {
    
    List<StockAlert> findByShopIdAndStatus(UUID shopId, StockAlert.AlertStatus status);
    
    @Query("SELECT sa FROM StockAlert sa WHERE sa.businessId = :businessId AND sa.status = 'ACTIVE'")
    List<StockAlert> findActiveAlerts(@Param("businessId") UUID businessId);
    
    @Query("SELECT sa FROM StockAlert sa WHERE sa.shopId = :shopId AND sa.status = 'ACTIVE'")
    List<StockAlert> findActiveAlertsByShop(@Param("shopId") UUID shopId);
    
    @Query("SELECT sa FROM StockAlert sa WHERE sa.item.id = :itemId AND sa.status = 'ACTIVE'")
    Optional<StockAlert> findActiveAlertByItem(@Param("itemId") UUID itemId);
    
    @Query("SELECT sa FROM StockAlert sa WHERE sa.businessId = :businessId " +
           "AND (:shopId IS NULL OR sa.shopId = :shopId) " +
           "AND (:status IS NULL OR sa.status = :status) " +
           "AND (:severity IS NULL OR sa.severity = :severity)")
    Page<StockAlert> findByFilters(@Param("businessId") UUID businessId,
                                    @Param("shopId") UUID shopId,
                                    @Param("status") StockAlert.AlertStatus status,
                                    @Param("severity") StockAlert.AlertSeverity severity,
                                    Pageable pageable);
}
