package com.jjenus.qliina_management.billing.repository;

import com.jjenus.qliina_management.billing.model.BillingPaymentMethod;
import com.jjenus.qliina_management.billing.model.PaymentMethodType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BillingPaymentMethodRepository extends JpaRepository<BillingPaymentMethod, UUID> {

    List<BillingPaymentMethod> findAllByBusinessIdOrderByIsDefaultDescCreatedAtAsc(UUID businessId);

    Optional<BillingPaymentMethod> findByBusinessIdAndType(UUID businessId, PaymentMethodType type);
}
