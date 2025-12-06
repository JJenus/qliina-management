package com.jjenus.qliina_management.inventory.repository;

import com.jjenus.qliina_management.inventory.model.PurchaseOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, UUID> {
    
    Optional<PurchaseOrder> findByPoNumber(String poNumber);
    
    @Query("SELECT po FROM PurchaseOrder po WHERE po.businessId = :businessId AND po.supplier.id = :supplierId")
    Page<PurchaseOrder> findBySupplierId(@Param("businessId") UUID businessId,
                                          @Param("supplierId") UUID supplierId,
                                          Pageable pageable);
    
    @Query("SELECT po FROM PurchaseOrder po WHERE po.businessId = :businessId AND po.shopId = :shopId")
    Page<PurchaseOrder> findByShopId(@Param("businessId") UUID businessId,
                                      @Param("shopId") UUID shopId,
                                      Pageable pageable);
    
    @Query("SELECT po FROM PurchaseOrder po WHERE po.businessId = :businessId AND po.status = :status")
    Page<PurchaseOrder> findByStatus(@Param("businessId") UUID businessId,
                                      @Param("status") PurchaseOrder.PurchaseOrderStatus status,
                                      Pageable pageable);
    
    @Query("SELECT po FROM PurchaseOrder po WHERE po.businessId = :businessId " +
           "AND (:supplierId IS NULL OR po.supplier.id = :supplierId) " +
           "AND (:shopId IS NULL OR po.shopId = :shopId) " +
           "AND (:status IS NULL OR po.status = :status) " +
           "AND (:fromDate IS NULL OR po.orderDate >= :fromDate) " +
           "AND (:toDate IS NULL OR po.orderDate <= :toDate)")
    Page<PurchaseOrder> findByFilters(@Param("businessId") UUID businessId,
                                       @Param("supplierId") UUID supplierId,
                                       @Param("shopId") UUID shopId,
                                       @Param("status") PurchaseOrder.PurchaseOrderStatus status,
                                       @Param("fromDate") LocalDateTime fromDate,
                                       @Param("toDate") LocalDateTime toDate,
                                       Pageable pageable);
    
    @Query("SELECT po FROM PurchaseOrder po WHERE po.status IN ('CONFIRMED', 'SHIPPED') " +
           "AND po.expectedDelivery < :date")
    List<PurchaseOrder> findOverdueOrders(@Param("date") LocalDate date);
    
    @Query("SELECT SUM(po.total) FROM PurchaseOrder po WHERE po.businessId = :businessId " +
           "AND po.orderDate BETWEEN :startDate AND :endDate")
    Double sumOrderValueByDateRange(@Param("businessId") UUID businessId,
                                     @Param("startDate") LocalDateTime startDate,
                                     @Param("endDate") LocalDateTime endDate);
}
