package com.jjenus.qliina_management.inventory.repository;

import com.jjenus.qliina_management.inventory.model.StockTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface StockTransactionRepository extends JpaRepository<StockTransaction, UUID> {
    
    Page<StockTransaction> findByShopId(UUID shopId, Pageable pageable);
    
    Page<StockTransaction> findByItemId(UUID itemId, Pageable pageable);
    
    @Query("SELECT st FROM StockTransaction st WHERE st.shopId = :shopId AND st.item.id = :itemId")
    Page<StockTransaction> findByShopIdAndItemId(@Param("shopId") UUID shopId,
                                                   @Param("itemId") UUID itemId,
                                                   Pageable pageable);
    
    @Query("SELECT st FROM StockTransaction st WHERE st.businessId = :businessId " +
           "AND (:shopId IS NULL OR st.shopId = :shopId) " +
           "AND (:itemId IS NULL OR st.item.id = :itemId) " +
           "AND (:type IS NULL OR st.type = :type) " +
           "AND (:fromDate IS NULL OR st.transactionDate >= :fromDate) " +
           "AND (:toDate IS NULL OR st.transactionDate <= :toDate)")
    Page<StockTransaction> findByFilters(@Param("businessId") UUID businessId,
                                          @Param("shopId") UUID shopId,
                                          @Param("itemId") UUID itemId,
                                          @Param("type") StockTransaction.TransactionType type,
                                          @Param("fromDate") LocalDateTime fromDate,
                                          @Param("toDate") LocalDateTime toDate,
                                          Pageable pageable);
    
    @Query("SELECT SUM(st.quantity) FROM StockTransaction st WHERE st.item.id = :itemId " +
           "AND st.type IN ('RECEIVED', 'TRANSFER_IN') " +
           "AND st.transactionDate BETWEEN :startDate AND :endDate")
    Double sumReceivedQuantity(@Param("itemId") UUID itemId,
                                @Param("startDate") LocalDateTime startDate,
                                @Param("endDate") LocalDateTime endDate);
    
    @Query("SELECT SUM(st.quantity) FROM StockTransaction st WHERE st.item.id = :itemId " +
           "AND st.type IN ('USED', 'WASTED', 'TRANSFER_OUT') " +
           "AND st.transactionDate BETWEEN :startDate AND :endDate")
    Double sumConsumedQuantity(@Param("itemId") UUID itemId,
                                @Param("startDate") LocalDateTime startDate,
                                @Param("endDate") LocalDateTime endDate);
}
