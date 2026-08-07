package com.jjenus.qliina_management.billing.service;

import com.jjenus.qliina_management.billing.model.BillingPaymentMethod;
import com.jjenus.qliina_management.billing.model.PaymentMethodType;
import com.jjenus.qliina_management.billing.repository.BillingPaymentMethodRepository;
import com.jjenus.qliina_management.common.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Stored billing methods for a business (MD §2 payment_methods).
 */
@Service
@RequiredArgsConstructor
public class BillingPaymentMethodService {

    private final BillingPaymentMethodRepository paymentMethodRepository;

    @Transactional(readOnly = true)
    public List<BillingPaymentMethod> list(UUID businessId) {
        return paymentMethodRepository.findAllByBusinessIdOrderByIsDefaultDescCreatedAtAsc(businessId);
    }

    @Transactional
    public BillingPaymentMethod add(UUID businessId, PaymentMethodType type, String label,
                                    String gatewayCustomerId, String gatewayMethodId) {
        List<BillingPaymentMethod> existing = paymentMethodRepository
                .findAllByBusinessIdOrderByIsDefaultDescCreatedAtAsc(businessId);
        BillingPaymentMethod method = BillingPaymentMethod.builder()
                .businessId(businessId)
                .type(type)
                .isDefault(existing.isEmpty())
                .label(label)
                .gatewayCustomerId(gatewayCustomerId)
                .gatewayMethodId(gatewayMethodId)
                .build();
        return paymentMethodRepository.save(method);
    }

    @Transactional
    public void remove(UUID businessId, UUID id) {
        BillingPaymentMethod method = paymentMethodRepository.findById(id)
                .filter(m -> m.getBusinessId().equals(businessId))
                .orElseThrow(() -> new BusinessException("Payment method not found", "PAYMENT_METHOD_NOT_FOUND"));
        paymentMethodRepository.delete(method);
        if (method.isDefault()) {
            paymentMethodRepository.findAllByBusinessIdOrderByIsDefaultDescCreatedAtAsc(businessId).stream()
                    .findFirst()
                    .ifPresent(m -> {
                        m.setDefault(true);
                        paymentMethodRepository.save(m);
                    });
        }
    }

    @Transactional
    public BillingPaymentMethod setDefault(UUID businessId, UUID id) {
        BillingPaymentMethod target = paymentMethodRepository.findById(id)
                .filter(m -> m.getBusinessId().equals(businessId))
                .orElseThrow(() -> new BusinessException("Payment method not found", "PAYMENT_METHOD_NOT_FOUND"));
        paymentMethodRepository.findAllByBusinessIdOrderByIsDefaultDescCreatedAtAsc(businessId)
                .forEach(m -> {
                    boolean nowDefault = m.getId().equals(target.getId());
                    if (m.isDefault() != nowDefault) {
                        m.setDefault(nowDefault);
                        paymentMethodRepository.save(m);
                    }
                });
        return target;
    }
}
