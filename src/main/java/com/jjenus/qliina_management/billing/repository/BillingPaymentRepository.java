package com.jjenus.qliina_management.billing.repository;

import com.jjenus.qliina_management.billing.model.BillingPayment;
import com.jjenus.qliina_management.billing.model.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BillingPaymentRepository extends JpaRepository<BillingPayment, UUID> {

    Optional<BillingPayment> findByIdempotencyKey(String idempotencyKey);

    Optional<BillingPayment> findByGatewayAndGatewayTransactionId(String gateway, String gatewayTransactionId);

    List<BillingPayment> findAllByInvoiceId(UUID invoiceId);

    boolean existsByInvoiceIdAndStatus(UUID invoiceId, PaymentStatus status);

    // -------------------------------------------------------------------------
    // Platform-admin analytics
    // -------------------------------------------------------------------------

    Page<BillingPayment> findByStatus(PaymentStatus status, Pageable pageable);

    long countByStatus(PaymentStatus status);

    /** Collected revenue since the given date (approved payments only). */
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM BillingPayment p " +
            "WHERE p.status = 'APPROVED' AND p.paidAt >= :since")
    BigDecimal sumApprovedSince(@Param("since") LocalDateTime since);

    /** Approved payments since the given date — for monthly trend aggregation. */
    @Query("SELECT p FROM BillingPayment p WHERE p.status = 'APPROVED' AND p.paidAt >= :since")
    List<BillingPayment> findApprovedSince(@Param("since") LocalDateTime since);
}
