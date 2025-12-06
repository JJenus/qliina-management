package com.jjenus.qliina_management.inventory.repository;

import com.jjenus.qliina_management.inventory.model.InventoryItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InventoryItemRepository extends JpaRepository<InventoryItem, UUID>, JpaSpecificationExecutor<InventoryItem> {
    
    Optional<InventoryItem> findBySku(String sku);
    
    @Query("SELECT i FROM InventoryItem i WHERE i.businessId = :businessId AND " +
           "(LOWER(i.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(i.sku) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(i.description) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<InventoryItem> searchItems(@Param("businessId") UUID businessId,
                                     @Param("search") String search,
                                     Pageable pageable);
    
    @Query("SELECT i FROM InventoryItem i WHERE i.businessId = :businessId AND i.category = :category")
    Page<InventoryItem> findByCategory(@Param("businessId") UUID businessId,
                                        @Param("category") InventoryItem.ItemCategory category,
                                        Pageable pageable);
    
    @Query("SELECT i FROM InventoryItem i WHERE i.businessId = :businessId AND i.supplierId = :supplierId")
    List<InventoryItem> findBySupplierId(@Param("businessId") UUID businessId,
                                          @Param("supplierId") UUID supplierId);
    
    @Query("SELECT i FROM InventoryItem i WHERE i.businessId = :businessId AND i.isActive = true")
    Page<InventoryItem> findActiveItems(@Param("businessId") UUID businessId, Pageable pageable);
    
    @Query("SELECT i FROM InventoryItem i WHERE i.businessId = :businessId AND i.currentStock <= i.reorderLevel")
    List<InventoryItem> findLowStockItems(@Param("businessId") UUID businessId);
    
    @Query("SELECT i FROM InventoryItem i WHERE i.businessId = :businessId AND i.currentStock <= i.reorderLevel / 2")
    List<InventoryItem> findCriticalStockItems(@Param("businessId") UUID businessId);
    
    boolean existsBySku(String sku);
}
