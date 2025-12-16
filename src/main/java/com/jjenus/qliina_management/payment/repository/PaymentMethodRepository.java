package com.jjenus.qliina_management.payment.repository;

import com.jjenus.qliina_management.payment.model.PaymentMethod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentMethodRepository extends JpaRepository<PaymentMethod, UUID> {
    
    List<PaymentMethod> findByBusinessIdAndIsActiveTrue(UUID businessId);
    
    Optional<PaymentMethod> findByBusinessIdAndType(UUID businessId, String type);
}
