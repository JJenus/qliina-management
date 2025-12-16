package com.jjenus.qliina_management.payment.repository;

import com.jjenus.qliina_management.payment.model.OrderPayment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface OrderPaymentRepository extends JpaRepository<OrderPayment, UUID> {
    
    List<OrderPayment> findByOrderId(UUID orderId);
    
    @Query("SELECT SUM(op.amount) FROM OrderPayment op WHERE op.orderId = :orderId")
    BigDecimal sumPaymentsByOrderId(@Param("orderId") UUID orderId);
    
    @Query("SELECT SUM(op.amount) FROM OrderPayment op WHERE op.businessId = :businessId AND (:shopId IS NULL OR op.shopId = :shopId) AND op.paidAt BETWEEN :startDate AND :endDate")
BigDecimal sumRevenueByDateRange(@Param("businessId") UUID businessId, @Param("shopId") UUID shopId, @Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);
    
    @Query("SELECT op FROM OrderPayment op WHERE op.businessId = :businessId AND " +
           "(:orderId IS NULL OR op.orderId = :orderId) AND " +
           "(:customerId IS NULL OR op.customerId = :customerId) AND " +
           "(:shopId IS NULL OR op.shopId = :shopId) AND " +
           "(:method IS NULL OR op.method = :method) AND " +
           "(:status IS NULL OR op.status = :status) AND " +
           "(:fromDate IS NULL OR op.paidAt >= :fromDate) AND " +
           "(:toDate IS NULL OR op.paidAt <= :toDate) AND " +
           "(:collectedBy IS NULL OR op.collectedBy = :collectedBy)")
    Page<OrderPayment> findByFilters(@Param("businessId") UUID businessId,
                                      @Param("orderId") UUID orderId,
                                      @Param("customerId") UUID customerId,
                                      @Param("shopId") UUID shopId,
                                      @Param("method") String method,
                                      @Param("status") String status,
                                      @Param("fromDate") LocalDateTime fromDate,
                                      @Param("toDate") LocalDateTime toDate,
                                      @Param("collectedBy") UUID collectedBy,
                                      Pageable pageable);
    
    @Query("SELECT SUM(op.amount) FROM OrderPayment op WHERE op.shopId = :shopId " +
           "AND op.paidAt BETWEEN :startDate AND :endDate AND op.method = 'CASH'")
    BigDecimal sumCashPaymentsByDateRange(@Param("shopId") UUID shopId,
                                          @Param("startDate") LocalDateTime startDate,
                                          @Param("endDate") LocalDateTime endDate);
}
