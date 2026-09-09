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
import java.util.Optional;
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

    @Query("SELECT COALESCE(SUM(op.amount), 0) FROM OrderPayment op WHERE op.orderId = :orderId AND op.status = 'COMPLETED'")
    BigDecimal sumCompletedPaymentsByOrderId(@Param("orderId") UUID orderId);

    Optional<OrderPayment> findByProviderAndProviderReference(String provider, String providerReference);

    /**
     * The single active (unsettled) PENDING payment for an order+provider — the
     * idempotency gate for link generation. Re-issuing a link re-presents an
     * existing authorization instead of stacking a duplicate charge.
     */
    @Query("SELECT op FROM OrderPayment op WHERE op.orderId = :orderId AND op.provider = :provider " +
           "AND op.status = 'PENDING' ORDER BY op.createdAt DESC")
    List<OrderPayment> findPendingByOrderAndProvider(
            @Param("orderId") UUID orderId, @Param("provider") String provider);

    /**
     * Abandoned PENDING authorizations for the expiry sweep. Only requests that
     * are genuinely awaiting the customer (no staff-review flag like
     * {@code AMOUNT_MISMATCH}) and older than the cutoff are candidates.
     */
    @Query("SELECT op FROM OrderPayment op WHERE op.status = 'PENDING' AND op.providerStatus = 'PENDING' " +
           "AND op.createdAt < :cutoff")
    List<OrderPayment> findExpiredPendingBefore(@Param("cutoff") LocalDateTime cutoff);
    
    @Query("SELECT op FROM OrderPayment op WHERE op.businessId = :businessId AND op.shopId = :shopId")
    Page<OrderPayment> findByBusinessIdAndShopId(
            @Param("businessId") UUID businessId,
            @Param("shopId") UUID shopId,
            Pageable pageable);
}