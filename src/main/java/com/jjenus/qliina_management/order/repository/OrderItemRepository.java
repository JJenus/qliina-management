package com.jjenus.qliina_management.order.repository;

import com.jjenus.qliina_management.order.model.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {
    
    List<OrderItem> findByOrderId(UUID orderId);
    
    @Query("SELECT oi FROM OrderItem oi WHERE oi.order.businessId = :businessId AND oi.barcode = :barcode")
    OrderItem findByBusinessIdAndBarcode(@Param("businessId") UUID businessId, @Param("barcode") String barcode);
    
    @Query("SELECT COUNT(oi) FROM OrderItem oi WHERE oi.order.shopId = :shopId AND oi.status = :status")
    Long countByShopIdAndStatus(@Param("shopId") UUID shopId, @Param("status") OrderItem.ItemStatus status);
}
