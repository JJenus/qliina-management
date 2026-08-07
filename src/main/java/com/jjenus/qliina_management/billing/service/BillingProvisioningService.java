package com.jjenus.qliina_management.billing.service;

import com.jjenus.qliina_management.billing.model.BillingPlan;
import com.jjenus.qliina_management.billing.model.PlanStatus;
import com.jjenus.qliina_management.billing.repository.BillingPlanRepository;
import com.jjenus.qliina_management.business.model.Business;
import com.jjenus.qliina_management.business.repository.BusinessRepository;
import com.jjenus.qliina_management.common.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Provisions the FREE trial subscription for a newly registered business (MD §2
 * plan_features / trial flow). Registration still succeeds even if provisioning
 * cannot complete — the missing subscription is back-filled by
 * {@link #ensureAllBusinessesProvisioned()} at startup.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BillingProvisioningService {

    private final BillingPlanRepository  billingPlanRepository;
    private final SubscriptionService    subscriptionService;
    private final BusinessRepository     businessRepository;

    @Transactional
    public void provisionTrial(UUID businessId, LocalDateTime trialEndsAt) {
        BillingPlan free = activeFreePlan();
        if (free == null) {
            log.warn("Cannot provision trial for businessId={}: no active FREE billing plan", businessId);
            return;
        }
        try {
            subscriptionService.createSubscription(
                    businessId, free.getId(), null, trialEndsAt, null);
            log.info("Provisioned FREE trial subscription for businessId={}", businessId);
        } catch (BusinessException e) {
            if ("SUBSCRIPTION_EXISTS".equals(e.getErrorCode())) {
                log.debug("businessId={} already has a subscription", businessId);
                return;
            }
            log.warn("Trial provisioning failed for businessId={}: {}", businessId, e.getMessage());
        }
    }

    /** Startup back-fill for businesses that registered before billing existed. */
    @Transactional
    public void ensureAllBusinessesProvisioned() {
        int provisioned = 0;
        for (Business business : businessRepository.findAll()) {
            provisionTrial(business.getId(), business.getTrialEndsAt());
            provisioned++;
        }
        log.info("Billing provisioning pass complete: {} businesses evaluated", provisioned);
    }

    private BillingPlan activeFreePlan() {
        return billingPlanRepository.findAll().stream()
                .filter(p -> p.getStatus() == PlanStatus.ACTIVE)
                .filter(p -> "FREE".equalsIgnoreCase(p.getName()))
                .findFirst()
                .orElse(null);
    }
}
