package com.jjenus.qliina_management.payment.repository;

import com.jjenus.qliina_management.payment.model.OrderPayment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface OrderPaymentRepository extends JpaRepository<OrderPayment, UUID>, JpaSpecificationExecutor<OrderPayment> {
    
    List<OrderPayment> findByOrderId(UUID orderId);
    
    @Query("SELECT COALESCE(SUM(op.amount), 0) FROM OrderPayment op WHERE op.orderId = :orderId")
    BigDecimal sumPaymentsByOrderId(@Param("orderId") UUID orderId);
    
    @Query("SELECT COALESCE(SUM(op.amount), 0) FROM OrderPayment op WHERE op.businessId = :businessId " +
           "AND (:shopId IS NULL OR op.shopId = :shopId) " +
           "AND op.paidAt BETWEEN :startDate AND :endDate")
    BigDecimal sumRevenueByDateRange(
            @Param("businessId") UUID businessId,
            @Param("shopId") UUID shopId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);
    
    @Query("SELECT COALESCE(SUM(op.amount), 0) FROM OrderPayment op WHERE op.shopId = :shopId " +
           "AND op.paidAt BETWEEN :startDate AND :endDate AND op.method = 'CASH'")
    BigDecimal sumCashPaymentsByDateRange(
            @Param("shopId") UUID shopId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);
    
    Page<OrderPayment> findByBusinessId(UUID businessId, Pageable pageable);
    
    @Query("SELECT op FROM OrderPayment op WHERE op.businessId = :businessId AND op.shopId = :shopId")
    Page<OrderPayment> findByBusinessIdAndShopId(
            @Param("businessId") UUID businessId,
            @Param("shopId") UUID shopId,
            Pageable pageable);
}