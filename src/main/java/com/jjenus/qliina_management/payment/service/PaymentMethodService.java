// ./src/main/java/com/jjenus/qliina_management/payment/service/PaymentMethodService.java
package com.jjenus.qliina_management.payment.service;

import com.jjenus.qliina_management.common.BusinessException;
import com.jjenus.qliina_management.payment.dto.CreatePaymentMethodRequest;
import com.jjenus.qliina_management.payment.dto.PaymentMethodDTO;
import com.jjenus.qliina_management.payment.dto.UpdatePaymentMethodRequest;
import com.jjenus.qliina_management.payment.model.PaymentMethod;
import com.jjenus.qliina_management.payment.repository.PaymentMethodRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentMethodService {

    private final PaymentMethodRepository paymentMethodRepository;

    @Transactional(readOnly = true)
    public List<PaymentMethodDTO> getActivePaymentMethods(UUID businessId) {
        // Get both global methods (null businessId) and business-specific methods
        List<PaymentMethod> methods = paymentMethodRepository.findByBusinessIdAndIsActiveTrue(businessId);
        
        // If no methods found for this business, create defaults
        if (methods.isEmpty()) {
            createDefaultMethodsForBusiness(businessId);
            methods = paymentMethodRepository.findByBusinessIdAndIsActiveTrue(businessId);
        }
        
        return methods.stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PaymentMethodDTO getPaymentMethod(UUID methodId) {
        PaymentMethod method = paymentMethodRepository.findById(methodId)
                .orElseThrow(() -> new BusinessException("Payment method not found", "METHOD_NOT_FOUND"));
        return mapToDTO(method);
    }

    @Transactional
    public PaymentMethodDTO createPaymentMethod(UUID businessId, CreatePaymentMethodRequest request) {
        // Check if method with this type already exists
        paymentMethodRepository.findByBusinessIdAndType(businessId, request.getType())
                .ifPresent(m -> {
                    throw new BusinessException(
                        "Payment method of this type already exists", 
                        "METHOD_EXISTS", 
                        "type"
                    );
                });

        PaymentMethod method = new PaymentMethod();
        method.setBusinessId(businessId);
        method.setName(request.getName());
        method.setType(request.getType().toUpperCase());
        method.setIcon(request.getIcon());
        method.setIsActive(true);
        method.setRequiresReference(request.getRequiresReference() != null ? 
                request.getRequiresReference() : false);
        method.setSurcharge(request.getSurcharge() != null ? 
                BigDecimal.valueOf(request.getSurcharge()) : null);
        method.setMinAmount(request.getMinAmount() != null ? 
                BigDecimal.valueOf(request.getMinAmount()) : null);
        method.setMaxAmount(request.getMaxAmount() != null ? 
                BigDecimal.valueOf(request.getMaxAmount()) : null);

        method = paymentMethodRepository.save(method);
        log.info("Payment method created: {} (type: {}) for business {}", 
                 method.getName(), method.getType(), businessId);
        
        return mapToDTO(method);
    }

    @Transactional
    public PaymentMethodDTO updatePaymentMethod(UUID methodId, UpdatePaymentMethodRequest request) {
        PaymentMethod method = paymentMethodRepository.findById(methodId)
                .orElseThrow(() -> new BusinessException("Payment method not found", "METHOD_NOT_FOUND"));

        if (request.getName() != null) {
            method.setName(request.getName());
        }
        if (request.getIcon() != null) {
            method.setIcon(request.getIcon());
        }
        if (request.getSurcharge() != null) {
            method.setSurcharge(BigDecimal.valueOf(request.getSurcharge()));
        }
        if (request.getMinAmount() != null) {
            method.setMinAmount(BigDecimal.valueOf(request.getMinAmount()));
        }
        if (request.getMaxAmount() != null) {
            method.setMaxAmount(BigDecimal.valueOf(request.getMaxAmount()));
        }

        method = paymentMethodRepository.save(method);
        return mapToDTO(method);
    }

    @Transactional
    public PaymentMethodDTO togglePaymentMethod(UUID methodId, boolean active) {
        PaymentMethod method = paymentMethodRepository.findById(methodId)
                .orElseThrow(() -> new BusinessException("Payment method not found", "METHOD_NOT_FOUND"));

        method.setIsActive(active);
        method = paymentMethodRepository.save(method);
        
        log.info("Payment method {} {}", method.getType(), active ? "enabled" : "disabled");
        return mapToDTO(method);
    }

    @Transactional
    public void deletePaymentMethod(UUID methodId) {
        PaymentMethod method = paymentMethodRepository.findById(methodId)
                .orElseThrow(() -> new BusinessException("Payment method not found", "METHOD_NOT_FOUND"));

        // Don't allow deleting system default methods
        if (isSystemMethod(method.getType())) {
            throw new BusinessException(
                "Cannot delete system payment method. Use disable instead.",
                "SYSTEM_METHOD_DELETE"
            );
        }

        paymentMethodRepository.delete(method);
        log.info("Payment method deleted: {}", method.getType());
    }

    @Transactional
    public void createDefaultMethodsForBusiness(UUID businessId) {
        log.info("Creating default payment methods for business: {}", businessId);
        
        createIfNotExists(businessId, "CASH", "Cash", "i-heroicons-banknotes", false);
        createIfNotExists(businessId, "CARD", "Credit/Debit Card", "i-heroicons-credit-card", false);
        createIfNotExists(businessId, "TRANSFER", "Bank Transfer", "i-heroicons-arrow-path", true);
        createIfNotExists(businessId, "WALLET", "Mobile Wallet", "i-heroicons-device-phone-mobile", true);
    }

    private void createIfNotExists(UUID businessId, String type, String name, 
                                    String icon, boolean requiresReference) {
        if (paymentMethodRepository.findByBusinessIdAndType(businessId, type).isEmpty()) {
            PaymentMethod method = new PaymentMethod();
            method.setBusinessId(businessId);
            method.setName(name);
            method.setType(type);
            method.setIcon(icon);
            method.setIsActive(true);
            method.setRequiresReference(requiresReference);
            paymentMethodRepository.save(method);
        }
    }

    private boolean isSystemMethod(String type) {
        return type.equals("CASH") || type.equals("CARD") || 
               type.equals("TRANSFER") || type.equals("WALLET");
    }

    private PaymentMethodDTO mapToDTO(PaymentMethod method) {
        return PaymentMethodDTO.builder()
                .id(method.getId())
                .name(method.getName())
                .type(method.getType())
                .icon(method.getIcon())
                .isActive(method.getIsActive())
                .requiresReference(method.getRequiresReference())
                .surcharge(method.getSurcharge() != null ? method.getSurcharge().doubleValue() : null)
                .minAmount(method.getMinAmount() != null ? method.getMinAmount().doubleValue() : null)
                .maxAmount(method.getMaxAmount() != null ? method.getMaxAmount().doubleValue() : null)
                .build();
    }
}