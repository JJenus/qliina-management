package com.jjenus.qliina_management.billing.repository;

import com.jjenus.qliina_management.billing.model.BillingPayment;
import com.jjenus.qliina_management.billing.model.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BillingPaymentRepository extends JpaRepository<BillingPayment, UUID> {

    Optional<BillingPayment> findByIdempotencyKey(String idempotencyKey);

    Optional<BillingPayment> findByGatewayAndGatewayTransactionId(String gateway, String gatewayTransactionId);

    List<BillingPayment> findAllByInvoiceId(UUID invoiceId);

    boolean existsByInvoiceIdAndStatus(UUID invoiceId, PaymentStatus status);
}
