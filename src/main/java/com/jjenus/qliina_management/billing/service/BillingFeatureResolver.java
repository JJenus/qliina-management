package com.jjenus.qliina_management.billing.service;

import com.jjenus.qliina_management.billing.model.PlanFeature;
import com.jjenus.qliina_management.billing.model.PlanStatus;
import com.jjenus.qliina_management.billing.model.Subscription;
import com.jjenus.qliina_management.billing.model.SubscriptionFeature;
import com.jjenus.qliina_management.billing.model.BillingPlan;
import com.jjenus.qliina_management.billing.repository.BillingPlanRepository;
import com.jjenus.qliina_management.billing.repository.PlanFeatureRepository;
import com.jjenus.qliina_management.billing.repository.SubscriptionFeatureRepository;
import com.jjenus.qliina_management.common.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Resolves the EFFECTIVE feature value for a business: plan feature, overridden
 * by a per-subscription override (MD §2 subscription_features). This is what
 * enforcement (PlanLimitService) queries — never the plan name.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BillingFeatureResolver {

    private final SubscriptionService subscriptionService;
    private final BillingPlanRepository billingPlanRepository;
    private final PlanFeatureRepository planFeatureRepository;
    private final SubscriptionFeatureRepository subscriptionFeatureRepository;

    @Transactional(readOnly = true)
    public String featureValue(UUID businessId, String featureKey) {
        Map<String, String> planValues = planValues(businessId);
        Map<String, String> overrideValues = overrideValues(businessId);
        return overrideValues.getOrDefault(featureKey, planValues.get(featureKey));
    }

    @Transactional(readOnly = true)
    public Map<String, String> allFeatures(UUID businessId) {
        Map<String, String> planValues = planValues(businessId);
        planValues.putAll(overrideValues(businessId));
        return planValues;
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
