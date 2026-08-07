package com.jjenus.qliina_management.billing.repository;

import com.jjenus.qliina_management.billing.model.BillingInvoice;
import com.jjenus.qliina_management.billing.model.InvoiceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface BillingInvoiceRepository extends JpaRepository<BillingInvoice, UUID> {

    Optional<BillingInvoice> findByIdempotencyKey(String idempotencyKey);

    Optional<BillingInvoice> findByInvoiceNumber(String invoiceNumber);

    Page<BillingInvoice> findAllBySubscriptionIdOrderByIssuedAtDesc(UUID subscriptionId, Pageable pageable);

    long countBySubscriptionIdAndStatus(UUID subscriptionId, InvoiceStatus status);
}
