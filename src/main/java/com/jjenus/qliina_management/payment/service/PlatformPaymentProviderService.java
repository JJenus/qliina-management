package com.jjenus.qliina_management.payment.service;

import com.jjenus.qliina_management.common.BusinessException;
import com.jjenus.qliina_management.payment.dto.AdminPaymentProviderDTO;
import com.jjenus.qliina_management.payment.model.PlatformPaymentProviderConfig;
import com.jjenus.qliina_management.payment.provider.PaymentProvider;
import com.jjenus.qliina_management.payment.provider.PaymentProviderRegistry;
import com.jjenus.qliina_management.payment.repository.PaymentProviderConfigRepository;
import com.jjenus.qliina_management.payment.repository.PlatformPaymentProviderConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import org.springframework.transaction.TransactionDefinition;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Platform-wide payment-provider availability (the "which gateways can businesses
 * connect at all" switch). A platform-disabled provider is invisible to business
 * gateway screens, its business config rows are treated as disabled, and every
 * checkout/connect attempt fails closed. Authorizations that were already
 * initiated keep verifying, refunding, and reconciling through webhooks — a
 * platform toggle never strands money already in flight.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformPaymentProviderService {

    /** A provider with no stored row was never touched by platform ops → available. */
    private static final boolean DEFAULT_PLATFORM_ENABLED = true;

    private final PaymentProviderRegistry registry;
    private final PlatformPaymentProviderConfigRepository repository;
    private final PaymentProviderConfigRepository businessConfigRepository;
    private final PlatformTransactionManager transactionManager;

    @Transactional(readOnly = true)
    public List<AdminPaymentProviderDTO> list() {
        Map<String, PlatformPaymentProviderConfig> rows = new LinkedHashMap<>();
        repository.findAll().forEach(c -> rows.put(c.getProvider(), c));

        return registry.all().stream()
                .sorted(Comparator.comparing(PaymentProvider::getName))
                .map(provider -> toDto(provider, rows.get(provider.getName())))
                .toList();
    }

    /**
     * No @Transactional here: the winner's row is created/updated inside a fresh
     * REQUIRES_NEW transaction per attempt, so a concurrent-toggle collision
     * (unique-constraint on a not-yet-created row, or @Version bump on an existing
     * one) is caught, the loser's transaction rolls back cleanly, and the intent is
     * re-applied to the winner's row. Platform toggles are admin-only and rare, so a
     * bounded 3-attempt loop is enough; exhaustion surfaces a 409 instead of a 500.
     */
    public AdminPaymentProviderDTO setPlatformEnabled(String providerName, boolean platformEnabled) {
        PaymentProvider provider = registry.require(providerName);
        for (int attempt = 0; ; attempt++) {
            try {
                PlatformPaymentProviderConfig row = toggleInNewTransaction(providerName, platformEnabled);
                log.info("Platform {} payment provider {} (by {})",
                        platformEnabled ? "enabled" : "disabled", providerName, row.getUpdatedByUsername());
                return toDto(provider, row);
            } catch (DataIntegrityViolationException | OptimisticLockingFailureException e) {
                if (attempt >= 2) {
                    throw new BusinessException(
                            "Another admin is updating " + providerName + " right now — please retry",
                            "PROVIDER_UPDATE_CONFLICT", "provider");
                }
                log.warn("Platform payment-provider toggle for {} raced, retrying (attempt {})",
                        providerName, attempt + 1);
            }
        }
    }

    private PlatformPaymentProviderConfig toggleInNewTransaction(String providerName, boolean platformEnabled) {
        TransactionTemplate tt = new TransactionTemplate(transactionManager);
        tt.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return tt.execute(status -> {
            PlatformPaymentProviderConfig row = repository.findByProvider(providerName)
                    .orElseGet(PlatformPaymentProviderConfig::new);
            row.setProvider(providerName);
            row.setPlatformEnabled(platformEnabled);
            row.setUpdatedByUsername(currentUsername());
            return repository.saveAndFlush(row);
        });
    }

    @Transactional(readOnly = true)
    public boolean isPlatformEnabled(String providerName) {
        return repository.findByProvider(providerName)
                .map(PlatformPaymentProviderConfig::isPlatformEnabled)
                .orElse(DEFAULT_PLATFORM_ENABLED);
    }

    /** All platform toggles in one query (provider → platformEnabled) for list paths. */
    @Transactional(readOnly = true)
    public Map<String, Boolean> platformEnabledMap() {
        Map<String, Boolean> result = new LinkedHashMap<>();
        repository.findAll().forEach(row -> result.put(row.getProvider(), row.isPlatformEnabled()));
        return result;
    }

    private AdminPaymentProviderDTO toDto(PaymentProvider provider, PlatformPaymentProviderConfig row) {
        return AdminPaymentProviderDTO.builder()
                .name(provider.getName())
                .displayName(provider.getDisplayName())
                .methods(provider.supportedMethods())
                .configured(provider.isConfigured())
                .platformEnabled(row == null ? DEFAULT_PLATFORM_ENABLED : row.isPlatformEnabled())
                .supportsPlatformSubaccounts(provider.supportsPlatformSubaccounts())
                .businessesConnected(businessConfigRepository.countByProviderAndEnabled(provider.getName(), true))
                .build();
    }

    private String currentUsername() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "system";
    }
}