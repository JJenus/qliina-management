package com.jjenus.qliina_management.billing.repository;

import com.jjenus.qliina_management.billing.model.BillingInvoice;
import com.jjenus.qliina_management.billing.model.InvoiceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BillingInvoiceRepository extends JpaRepository<BillingInvoice, UUID> {

    Optional<BillingInvoice> findByIdempotencyKey(String idempotencyKey);

    Optional<BillingInvoice> findByInvoiceNumber(String invoiceNumber);

    Page<BillingInvoice> findAllBySubscriptionIdOrderByIssuedAtDesc(UUID subscriptionId, Pageable pageable);

    long countBySubscriptionIdAndStatus(UUID subscriptionId, InvoiceStatus status);

    // -------------------------------------------------------------------------
    // Platform-admin listings & analytics
    // -------------------------------------------------------------------------

    Page<BillingInvoice> findByStatus(InvoiceStatus status, Pageable pageable);

    Page<BillingInvoice> findBySubscriptionIdIn(Collection<UUID> subscriptionIds, Pageable pageable);

    Page<BillingInvoice> findBySubscriptionIdInAndStatus(Collection<UUID> subscriptionIds, InvoiceStatus status, Pageable pageable);

    /** All invoices issued since the given date (revenue trend aggregation). */
    Page<BillingInvoice> findByIssuedAtAfter(LocalDateTime since, Pageable pageable);

    /** Outstanding (OPEN) invoice total. */
    @Query("SELECT COALESCE(SUM(i.amount), 0) FROM BillingInvoice i WHERE i.status = 'OPEN'")
    BigDecimal sumOpenAmount();

    /** Total invoiced since the given date. */
    @Query("SELECT COALESCE(SUM(i.amount), 0) FROM BillingInvoice i WHERE i.issuedAt >= :since")
    BigDecimal sumIssuedSince(@Param("since") LocalDateTime since);

    long countByStatus(InvoiceStatus status);
}
