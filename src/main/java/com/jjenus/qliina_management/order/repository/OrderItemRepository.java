package com.jjenus.qliina_management.order.repository;

import com.jjenus.qliina_management.order.model.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;
import java.time.LocalDateTime;


@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {
    
    List<OrderItem> findByOrderId(UUID orderId);
    
    @Query("SELECT oi FROM OrderItem oi WHERE oi.order.businessId = :businessId AND oi.barcode = :barcode")
    OrderItem findByBusinessIdAndBarcode(@Param("businessId") UUID businessId, @Param("barcode") String barcode);
    
    @Query("SELECT COUNT(oi) FROM OrderItem oi WHERE oi.order.shopId = :shopId AND oi.status = :status")
    Long countByShopIdAndStatus(@Param("shopId") UUID shopId, @Param("status") OrderItem.ItemStatus status);
 
  
    @Query("SELECT oi FROM OrderItem oi WHERE oi.order.businessId = :businessId " +
           "AND (:shopId IS NULL OR oi.order.shopId = :shopId) " +
           "AND oi.status IN :statuses")
    Page<OrderItem> findByBusinessIdAndStatusIn(
            @Param("businessId") UUID businessId,
            @Param("shopId") UUID shopId,
            @Param("statuses") List<OrderItem.ItemStatus> statuses,
            Pageable pageable);

    @Query("SELECT COUNT(oi) FROM OrderItem oi WHERE oi.order.businessId = :businessId " +
           "AND oi.order.shopId = :shopId AND oi.status = :status")
    long countByBusinessIdAndShopIdAndStatus(
            @Param("businessId") UUID businessId,
            @Param("shopId") UUID shopId,
            @Param("status") OrderItem.ItemStatus status);

    @Query("SELECT COUNT(oi) FROM OrderItem oi JOIN ItemStatusHistory ish ON ish.orderItem.id = oi.id " +
           "WHERE oi.status = :status AND ish.updatedBy = :workerId " +
           "AND ish.timestamp BETWEEN :start AND :end")
    Long countByStatusAndWorkerRoleAndDateRange(
            @Param("status") OrderItem.ItemStatus status,
            @Param("workerId") UUID workerId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

}