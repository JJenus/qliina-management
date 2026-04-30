// ./src/main/java/com/jjenus/qliina_management/common/config/PaymentMethodInitializer.java
package com.jjenus.qliina_management.common.config;

import com.jjenus.qliina_management.payment.model.PaymentMethod;
import com.jjenus.qliina_management.payment.repository.PaymentMethodRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Seeds default payment methods into every new business on creation.
 * These are the standard methods available out-of-the-box.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(2) // Run after DataInitializer
public class PaymentMethodInitializer implements CommandLineRunner {

    private final PaymentMethodRepository paymentMethodRepository;
    
    // System business ID - payment methods can be global (businessId = null) 
    // or per-business
    private static final UUID SYSTEM_BUSINESS_ID = 
            UUID.fromString("00000000-0000-0000-0000-000000000000");

    @Override
    @Transactional
    public void run(String... args) {
        // Create global/system-wide payment methods if they don't exist
        createDefaultPaymentMethods();
        
        log.info("Payment methods initialization complete. Count: {}", 
                 paymentMethodRepository.count());
    }

    private void createDefaultPaymentMethods() {
        List<PaymentMethodConfig> defaultMethods = Arrays.asList(
            new PaymentMethodConfig("CASH", "Cash", "i-heroicons-banknotes", 
                false, null, new BigDecimal("0.01"), new BigDecimal("99999.99")),
            
            new PaymentMethodConfig("CARD", "Credit/Debit Card", "i-heroicons-credit-card", 
                false, new BigDecimal("2.5"), new BigDecimal("0.50"), null),
            
            new PaymentMethodConfig("TRANSFER", "Bank Transfer", "i-heroicons-arrow-path", 
                true, null, new BigDecimal("1.00"), null),
            
            new PaymentMethodConfig("WALLET", "Mobile Wallet", "i-heroicons-device-phone-mobile", 
                true, null, new BigDecimal("0.50"), new BigDecimal("5000.00")),
            
            new PaymentMethodConfig("CORPORATE", "Corporate Account", "i-heroicons-building-office", 
                false, null, null, null)
        );

        for (PaymentMethodConfig config : defaultMethods) {
            createIfNotExists(config);
        }
    }

    private void createIfNotExists(PaymentMethodConfig config) {
        boolean exists = paymentMethodRepository.findByBusinessIdAndType(
                SYSTEM_BUSINESS_ID, config.type).isPresent();
        
        if (!exists) {
            PaymentMethod method = new PaymentMethod();
            method.setBusinessId(SYSTEM_BUSINESS_ID);
            method.setName(config.name);
            method.setType(config.type);
            method.setIcon(config.icon);
            method.setIsActive(true);
            method.setRequiresReference(config.requiresReference);
            method.setSurcharge(config.surcharge);
            method.setMinAmount(config.minAmount);
            method.setMaxAmount(config.maxAmount);
            
            paymentMethodRepository.save(method);
            log.info("Created payment method: {}", config.type);
        }
    }

    /**
     * Creates default payment methods for a newly registered business.
     * Called by BusinessService.registerBusiness().
     */
    @Transactional
    public void createDefaultMethodsForBusiness(UUID businessId) {
        List<PaymentMethodConfig> defaultMethods = Arrays.asList(
            new PaymentMethodConfig("CASH", "Cash", "i-heroicons-banknotes", 
                false, null, null, null),
            new PaymentMethodConfig("CARD", "Card", "i-heroicons-credit-card", 
                false, null, null, null),
            new PaymentMethodConfig("TRANSFER", "Transfer", "i-heroicons-arrow-path", 
                true, null, null, null)
        );

        for (PaymentMethodConfig config : defaultMethods) {
            PaymentMethod method = new PaymentMethod();
            method.setBusinessId(businessId);
            method.setName(config.name);
            method.setType(config.type);
            method.setIcon(config.icon);
            method.setIsActive(true);
            method.setRequiresReference(config.requiresReference);
            
            paymentMethodRepository.save(method);
        }
        
        log.info("Created default payment methods for business: {}", businessId);
    }

    private static class PaymentMethodConfig {
        final String type;
        final String name;
        final String icon;
        final boolean requiresReference;
        final BigDecimal surcharge;
        final BigDecimal minAmount;
        final BigDecimal maxAmount;

        PaymentMethodConfig(String type, String name, String icon, 
                           boolean requiresReference, BigDecimal surcharge,
                           BigDecimal minAmount, BigDecimal maxAmount) {
            this.type = type;
            this.name = name;
            this.icon = icon;
            this.requiresReference = requiresReference;
            this.surcharge = surcharge;
            this.minAmount = minAmount;
            this.maxAmount = maxAmount;
        }
    }
}