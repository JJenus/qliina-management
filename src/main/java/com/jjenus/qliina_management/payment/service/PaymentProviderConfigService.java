package com.jjenus.qliina_management.payment.service;

import com.jjenus.qliina_management.payment.dto.PaymentProviderDTO;
import com.jjenus.qliina_management.payment.model.PaymentProviderConfig;
import com.jjenus.qliina_management.payment.provider.PaymentProvider;
import com.jjenus.qliina_management.payment.provider.PaymentProviderRegistry;
import com.jjenus.qliina_management.payment.repository.PaymentProviderConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Per-business payment-provider enablement. Only explicit admin overrides are
 * stored; a business with no row falls back to {@link #DEFAULT_ENABLED}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentProviderConfigService {

    /** Real processors require explicit merchant opt-in; simulator is on by default. */
    private static final Map<String, Boolean> DEFAULT_ENABLED = Map.of("simulator", true);

    private final PaymentProviderRegistry registry;
    private final PaymentProviderConfigRepository repository;

    @Transactional
    public void setEnabled(UUID businessId, String providerName, boolean enabled) {
        PaymentProvider provider = registry.require(providerName);
        PaymentProviderConfig config = repository.findByBusinessIdAndProvider(businessId, provider.getName())
                .orElseGet(() -> {
                    PaymentProviderConfig created = new PaymentProviderConfig();
                    created.setBusinessId(businessId);
                    created.setProvider(provider.getName());
                    return created;
                });
        config.setEnabled(enabled);
        repository.save(config);
        log.info("Business {} {} provider {}", businessId, enabled ? "enabled" : "disabled", provider.getName());
    }

    @Transactional(readOnly = true)
    public boolean isEnabled(UUID businessId, String providerName) {
        return repository.findByBusinessIdAndProvider(businessId, providerName)
                .map(PaymentProviderConfig::isEnabled)
                .orElseGet(() -> DEFAULT_ENABLED.getOrDefault(providerName, false));
    }

    @Transactional(readOnly = true)
    public List<PaymentProviderDTO> list(UUID businessId) {
        Map<String, Boolean> overrides = new LinkedHashMap<>();
        repository.findByBusinessId(businessId)
                .forEach(c -> overrides.put(c.getProvider(), c.isEnabled()));

        return registry.all().stream()
                .sorted(java.util.Comparator.comparing(PaymentProvider::getName))
                .map(provider -> {
                    boolean enabled = overrides.getOrDefault(provider.getName(),
                            DEFAULT_ENABLED.getOrDefault(provider.getName(), false));
                    boolean configured = provider.isConfigured();
                    return PaymentProviderDTO.builder()
                            .name(provider.getName())
                            .displayName(provider.getDisplayName())
                            .methods(provider.supportedMethods())
                            .enabled(enabled)
                            .configured(configured)
                            .available(enabled && configured)
                            .build();
                })
                .toList();
    }
}