package com.jjenus.qliina_management.payment.repository;

import com.jjenus.qliina_management.payment.model.PaymentReconciliationItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentReconciliationItemRepository extends JpaRepository<PaymentReconciliationItem, UUID> {

    Optional<PaymentReconciliationItem> findByProviderAndProviderReference(String provider, String providerReference);

    List<PaymentReconciliationItem> findByStatusOrderByReceivedAtDesc(String status);

    List<PaymentReconciliationItem> findByBusinessIdAndStatusOrderByReceivedAtDesc(UUID businessId, String status);
}