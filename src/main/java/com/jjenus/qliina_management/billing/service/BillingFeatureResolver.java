package com.jjenus.qliina_management.billing.service;

import com.jjenus.qliina_management.billing.model.PlanFeature;
import com.jjenus.qliina_management.billing.model.PlanStatus;
import com.jjenus.qliina_management.billing.model.Subscription;
import com.jjenus.qliina_management.billing.model.SubscriptionFeature;
import com.jjenus.qliina_management.billing.model.SubscriptionStatus;
import com.jjenus.qliina_management.billing.model.BillingPlan;
import com.jjenus.qliina_management.billing.repository.BillingPlanRepository;
import com.jjenus.qliina_management.billing.repository.PlanFeatureRepository;
import com.jjenus.qliina_management.billing.repository.SubscriptionFeatureRepository;
import com.jjenus.qliina_management.common.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Resolves the EFFECTIVE feature value for a business: plan feature, overridden
 * by a per-subscription override (MD §2 subscription_features). This is what
 * enforcement (PlanLimitService) queries — never the plan name.
 *
 * <p>Every new business starts with a 30-day trial during which ALL features
 * are enabled: hard limits resolve to unlimited ({@code -1}) and feature flags
 * to {@code true}. Explicit per-subscription overrides always take precedence.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BillingFeatureResolver {

    private final SubscriptionService subscriptionService;
    private final BillingPlanRepository billingPlanRepository;
    private final PlanFeatureRepository planFeatureRepository;
    private final SubscriptionFeatureRepository subscriptionFeatureRepository;

    /** Numeric hard-limit keys — during trial these resolve to unlimited. */
    private static final Set<String> HARD_LIMIT_KEYS = Set.of(
            "max_shops", "max_users", "max_employees", "max_orders_per_month",
            "max_service_catalog_items", "max_inventory_items", "data_retention_days");

    @Transactional(readOnly = true)
    public String featureValue(UUID businessId, String featureKey) {
        Map<String, String> overrideValues = overrideValues(businessId);
        if (overrideValues.containsKey(featureKey)) {
            return overrideValues.get(featureKey);
        }
        if (isInTrial(businessId)) {
            return trialValue(featureKey);
        }
        return planValues(businessId).get(featureKey);
    }

    @Transactional(readOnly = true)
    public Map<String, String> allFeatures(UUID businessId) {
        Map<String, String> values = planValues(businessId);
        Map<String, String> overrides = overrideValues(businessId);
        values.putAll(overrides);
        if (isInTrial(businessId)) {
            values.replaceAll((key, value) -> overrides.containsKey(key) ? value : trialValue(key));
        }
        return values;
    }

    /** Typed helpers — feature values are strings per the schema. */

    public int intValue(UUID businessId, String key, int fallback) {
        String v = featureValue(businessId, key);
        if (v == null || v.isBlank()) return fallback;
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public boolean boolValue(UUID businessId, String key, boolean fallback) {
        String v = featureValue(businessId, key);
        if (v == null) return fallback;
        return Boolean.parseBoolean(v);
    }

    private Map<String, String> planValues(UUID businessId) {
        Subscription sub = findLiveOrNull(businessId);
        UUID planId = sub != null ? sub.getPlan().getId() : freePlanId();
        if (planId == null) {
            return Map.of();
        }
        return planFeatureRepository.findAllByPlanId(planId).stream()
                .collect(Collectors.toMap(PlanFeature::getFeatureKey, PlanFeature::getValue));
    }

    private Map<String, String> overrideValues(UUID businessId) {
        Subscription sub = findLiveOrNull(businessId);
        if (sub == null) {
            return Map.of();
        }
        return subscriptionFeatureRepository.findAllBySubscriptionId(sub.getId()).stream()
                .collect(Collectors.toMap(SubscriptionFeature::getFeatureKey, SubscriptionFeature::getValue));
    }

    /**
     * The effective plan for a business (live subscription, else FREE). Never throws.
     */
    private Subscription findLiveOrNull(UUID businessId) {
        try {
            return subscriptionService.getLiveSubscription(businessId);
        } catch (BusinessException e) {
            return null;
        }
    }

    /** True while the business's trial subscription is still running. */
    private boolean isInTrial(UUID businessId) {
        Subscription sub = findLiveOrNull(businessId);
        return sub != null
                && sub.getStatus() == SubscriptionStatus.TRIALING
                && sub.getTrialEndsAt() != null
                && sub.getTrialEndsAt().isAfter(LocalDateTime.now());
    }

    /** All-features value used during the free trial: unlimited limits, flags on. */
    private String trialValue(String featureKey) {
        return HARD_LIMIT_KEYS.contains(featureKey) ? "-1" : "true";
    }

    private UUID freePlanId() {
        return billingPlanRepository.findAll().stream()
                .filter(p -> p.getStatus() == PlanStatus.ACTIVE)
                .filter(p -> "FREE".equalsIgnoreCase(p.getName()))
                .findFirst()
                .map(BillingPlan::getId)
                .orElseGet(() -> {
                    log.warn("No active FREE billing plan found; feature resolution will be empty");
                    return null;
                });
    }
}
