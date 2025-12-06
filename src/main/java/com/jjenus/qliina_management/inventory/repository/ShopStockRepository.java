package com.jjenus.qliina_management.inventory.repository;

import com.jjenus.qliina_management.inventory.model.ShopStock;
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
public interface ShopStockRepository extends JpaRepository<ShopStock, UUID> {
    
    Optional<ShopStock> findByShopIdAndItemId(UUID shopId, UUID itemId);
    
    @Query("SELECT ss FROM ShopStock ss WHERE ss.shopId = :shopId")
    Page<ShopStock> findByShopId(@Param("shopId") UUID shopId, Pageable pageable);
    
    @Query("SELECT ss FROM ShopStock ss WHERE ss.item.businessId = :businessId AND ss.shopId = :shopId")
    Page<ShopStock> findByBusinessIdAndShopId(@Param("businessId") UUID businessId,
                                               @Param("shopId") UUID shopId,
                                               Pageable pageable);
    
    @Query("SELECT ss FROM ShopStock ss WHERE ss.shopId = :shopId AND ss.status = :status")
    List<ShopStock> findByShopIdAndStatus(@Param("shopId") UUID shopId,
                                           @Param("status") ShopStock.StockStatus status);
    
    @Query("SELECT ss FROM ShopStock ss WHERE ss.item.businessId = :businessId " +
           "AND ss.status IN ('LOW', 'CRITICAL', 'OUT_OF_STOCK')")
    List<ShopStock> findShopsWithLowStock(@Param("businessId") UUID businessId);
    
    @Query("SELECT COUNT(ss) FROM ShopStock ss WHERE ss.shopId = :shopId AND ss.status = 'OUT_OF_STOCK'")
    long countOutOfStockItems(@Param("shopId") UUID shopId);
    
    @Query("SELECT COUNT(ss) FROM ShopStock ss WHERE ss.shopId = :shopId AND ss.status IN (:statuses)")
long countByShopIdAndStatus(@Param("shopId") UUID shopId, @Param("statuses") ShopStock.StockStatus... statuses);
}
