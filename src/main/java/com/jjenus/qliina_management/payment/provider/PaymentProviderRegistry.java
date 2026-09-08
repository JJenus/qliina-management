package com.jjenus.qliina_management.payment.provider;

import com.jjenus.qliina_management.common.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/** Holds every {@link PaymentProvider} bean, resolved by lower-cased name. */
@Slf4j
@Component
public class PaymentProviderRegistry {

    private final Map<String, PaymentProvider> providers = new LinkedHashMap<>();

    public PaymentProviderRegistry(Collection<PaymentProvider> providerBeans) {
        providerBeans.forEach(p -> {
            PaymentProvider previous = providers.put(p.getName().toLowerCase(), p);
            if (previous != null) {
                throw new IllegalStateException("Duplicate payment provider name: " + p.getName());
            }
        });
        log.info("Registered payment providers: {}", providers.keySet());
    }

    public PaymentProvider require(String name) {
        PaymentProvider provider = providers.get(name == null ? null : name.trim().toLowerCase());
        if (provider == null) {
            throw new BusinessException("Unknown payment provider: " + name, "PROVIDER_UNKNOWN", "provider");
        }
        return provider;
    }

    public Collection<PaymentProvider> all() {
        return providers.values();
    }
}