package com.jjenus.qliina_management.payment.service;

import com.jjenus.qliina_management.common.BusinessException;
import com.jjenus.qliina_management.common.security.EncryptionService;
import com.jjenus.qliina_management.payment.dto.PaymentProviderDTO;
import com.jjenus.qliina_management.payment.dto.UpdateProviderConnectionRequest;
import com.jjenus.qliina_management.payment.model.PaymentProviderConfig;
import com.jjenus.qliina_management.payment.model.ProviderConnectionMode;
import com.jjenus.qliina_management.payment.provider.PaymentProvider;
import com.jjenus.qliina_management.payment.provider.PaymentProviderRegistry;
import com.jjenus.qliina_management.payment.repository.PaymentProviderConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Per-business payment-provider enablement and connection model. Only explicit
 * admin overrides are stored; a business with no row falls back to
 * {@link #DEFAULT_ENABLED} and {@link #DEFAULT_MODE}.
 *
 * <p>A provider is checkout-eligible ({@code available}) when it is enabled and
 * connectable: {@code PLATFORM} mode requires the platform to be configured,
 * {@code BYO} mode requires business credentials (encrypted at rest). Manual
 * POS recording of CARD/TRANSFER never depends on any provider being available.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentProviderConfigService {

    /** Real processors require explicit merchant opt-in; simulator is on by default. */
    private static final Map<String, Boolean> DEFAULT_ENABLED = Map.of("simulator", true);

    /** Simulator is platform-connected by default; real processors default to no gateway. */
    private static final Map<String, ProviderConnectionMode> DEFAULT_MODE =
            Map.of("simulator", ProviderConnectionMode.PLATFORM);

    private final PaymentProviderRegistry registry;
    private final PaymentProviderConfigRepository repository;
    private final EncryptionService encryptionService;

    @Transactional
    public void setEnabled(UUID businessId, String providerName, boolean enabled) {
        PaymentProvider provider = registry.require(providerName);
        PaymentProviderConfig config = findOrDefault(businessId, provider);
        config.setEnabled(enabled);
        repository.save(config);
        log.info("Business {} {} provider {}", businessId, enabled ? "enabled" : "disabled", provider.getName());
    }

    @Transactional
    public PaymentProviderConfig setProviderConnection(UUID businessId, String providerName,
            UpdateProviderConnectionRequest request) {
        PaymentProvider provider = registry.require(providerName);
        ProviderConnectionMode mode;
        try {
            mode = ProviderConnectionMode.valueOf(StringUtils.hasText(request.getMode())
                    ? request.getMode().trim().toUpperCase() : "DISCONNECTED");
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Unsupported connection mode: " + request.getMode(),
                    "PROVIDER_CONFIG_INVALID", "mode");
        }

        PaymentProviderConfig config = findOrDefault(businessId, provider);
        switch (mode) {
            case PLATFORM -> {
                if (StringUtils.hasText(request.getPlatformSubaccountId())
                        && !provider.supportsPlatformSubaccounts()) {
                    throw new BusinessException(provider.getDisplayName()
                                    + " does not support platform subaccounts",
                            "PROVIDER_CONFIG_INVALID", "platformSubaccountId");
                }
                // Calling the provider's platform requires the platform to be
                // reachable at charge time; keep it enabled and let available()
                // reflect configuration state.
                config.setEnabled(true);
                config.setConnectionMode(ProviderConnectionMode.PLATFORM);
                config.setPlatformSubaccountId(trimToNull(request.getPlatformSubaccountId()));
                config.setCredentialsEncrypted(null);
            }
            case BYO -> {
                String secret = trimToNull(request.getSecretKey());
                if (secret == null) {
                    throw new BusinessException("A secret key is required for bring-your-own credentials",
                            "PROVIDER_CONFIG_INVALID", "secretKey");
                }
                config.setEnabled(true);
                config.setConnectionMode(ProviderConnectionMode.BYO);
                config.setCredentialsEncrypted(encryptionService.encrypt(secret));
                config.setPlatformSubaccountId(null);
                log.info("Business {} configured BYO credentials for provider {}", businessId, provider.getName());
            }
            case DISCONNECTED -> {
                config.setEnabled(false);
                config.setConnectionMode(ProviderConnectionMode.DISCONNECTED);
                config.setCredentialsEncrypted(null);
                config.setPlatformSubaccountId(null);
                log.info("Business {} disconnected provider {}", businessId, provider.getName());
            }
        }
        return repository.save(config);
    }

    /**
     * Resolves the runtime connection context for a charge: the stored mode,
     * subaccount, and the decrypted BYO credentials (when present).
     */
    @Transactional(readOnly = true)
    public PaymentProvider.Connection resolveConnection(UUID businessId, PaymentProvider provider) {
        PaymentProviderConfig config = repository
                .findByBusinessIdAndProvider(businessId, provider.getName())
                .orElseGet(() -> {
                    PaymentProviderConfig created = new PaymentProviderConfig();
                    created.setBusinessId(businessId);
                    created.setProvider(provider.getName());
                    return created;
                });
        ProviderConnectionMode mode = config.getConnectionMode() != null
                ? config.getConnectionMode()
                : DEFAULT_MODE.getOrDefault(provider.getName(), ProviderConnectionMode.DISCONNECTED);
        String subaccount = config.getPlatformSubaccountId();
        String credentials = null;
        if (mode == ProviderConnectionMode.BYO && StringUtils.hasText(config.getCredentialsEncrypted())) {
            credentials = encryptionService.decrypt(config.getCredentialsEncrypted());
        }
        return new PaymentProvider.Connection(mode.name(), subaccount, credentials);
    }

    @Transactional(readOnly = true)
    public boolean isEnabled(UUID businessId, String providerName) {
        return repository.findByBusinessIdAndProvider(businessId, providerName)
                .map(PaymentProviderConfig::isEnabled)
                .orElseGet(() -> DEFAULT_ENABLED.getOrDefault(providerName, false));
    }

    @Transactional(readOnly = true)
    public List<PaymentProviderDTO> list(UUID businessId) {
        Map<String, PaymentProviderConfig> overrides = new java.util.LinkedHashMap<>();
        repository.findByBusinessId(businessId)
                .forEach(c -> overrides.put(c.getProvider(), c));

        return registry.all().stream()
                .sorted(Comparator.comparing(PaymentProvider::getName))
                .map(provider -> toDto(provider, overrides.get(provider.getName())))
                .toList();
    }

    private PaymentProviderConfig findOrDefault(UUID businessId, PaymentProvider provider) {
        return repository.findByBusinessIdAndProvider(businessId, provider.getName())
                .orElseGet(() -> {
                    PaymentProviderConfig created = new PaymentProviderConfig();
                    created.setBusinessId(businessId);
                    created.setProvider(provider.getName());
                    return created;
                });
    }

    private PaymentProviderDTO toDto(PaymentProvider provider, PaymentProviderConfig config) {
        boolean enabled = config != null
                ? config.isEnabled()
                : DEFAULT_ENABLED.getOrDefault(provider.getName(), false);
        ProviderConnectionMode mode = config != null && config.getConnectionMode() != null
                ? config.getConnectionMode()
                : DEFAULT_MODE.getOrDefault(provider.getName(), ProviderConnectionMode.DISCONNECTED);
        boolean platformConfigured = provider.isConfigured();
        boolean hasCredentials = config != null
                && mode == ProviderConnectionMode.BYO
                && StringUtils.hasText(config.getCredentialsEncrypted());
        boolean connectable = switch (mode) {
            case PLATFORM -> platformConfigured;
            case BYO -> hasCredentials;
            case DISCONNECTED -> false;
        };
        boolean available = enabled && connectable;
        return PaymentProviderDTO.builder()
                .name(provider.getName())
                .displayName(provider.getDisplayName())
                .methods(provider.supportedMethods())
                .enabled(enabled)
                .configured(platformConfigured)
                .available(available)
                .connectionMode(mode)
                .hasCredentials(hasCredentials)
                .platformSubaccountId(config != null ? config.getPlatformSubaccountId() : null)
                .supportsPlatformSubaccounts(provider.supportsPlatformSubaccounts())
                .build();
    }

    private static String trimToNull(String value) {
        String trimmed = value == null ? null : value.trim();
        return StringUtils.hasText(trimmed) ? trimmed : null;
    }
}