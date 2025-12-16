package com.jjenus.qliina_management.payment.repository;

import com.jjenus.qliina_management.payment.model.Refund;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface RefundRepository extends JpaRepository<Refund, UUID> {
    
    List<Refund> findByPaymentId(UUID paymentId);
    
    @Query("SELECT SUM(r.amount) FROM Refund r WHERE r.payment.id = :paymentId")
    BigDecimal sumRefundsByPaymentId(@Param("paymentId") UUID paymentId);
    
    @Query("SELECT SUM(r.amount) FROM Refund r WHERE r.payment.shopId = :shopId " +
           "AND r.processedAt BETWEEN :startDate AND :endDate AND r.method = 'CASH'")
    BigDecimal sumCashRefundsByDateRange(@Param("shopId") UUID shopId,
                                         @Param("startDate") LocalDateTime startDate,
                                         @Param("endDate") LocalDateTime endDate);
}
