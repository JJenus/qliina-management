package com.jjenus.qliina_management.billing.service;

import com.jjenus.qliina_management.billing.model.*;
import com.jjenus.qliina_management.billing.repository.BillingPlanRepository;
import com.jjenus.qliina_management.billing.repository.PlanFeatureRepository;
import com.jjenus.qliina_management.billing.repository.PlanVersionRepository;
import com.jjenus.qliina_management.billing.repository.SubscriptionRepository;
import com.jjenus.qliina_management.common.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Plan/version/feature management (MD §2, §5). Plans are soft-state:
 * deprecated → no longer offered; archived → historical only. Hard delete is
 * refused while any subscription references the plan.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlanManagementService {

    private final BillingPlanRepository planRepository;
    private final PlanVersionRepository versionRepository;
    private final PlanFeatureRepository featureRepository;
    private final SubscriptionRepository subscriptionRepository;

    // ---------------------------------------------------------------------
    // Plan CRUD
    // ---------------------------------------------------------------------

    @Transactional
    public BillingPlan createPlan(String name, String description, BigDecimal price, String currency,
                                  LocalDateTime effectiveFrom, List<PlanFeature> features) {
        if (planRepository.existsByName(name)) {
            throw new BusinessException("A plan with name '" + name + "' already exists", "PLAN_EXISTS", "name");
        }
        BillingPlan plan = BillingPlan.builder()
                .name(name)
                .description(description)
                .status(PlanStatus.ACTIVE)
                .build();
        BillingPlan saved = planRepository.save(plan);

        PlanVersion version = PlanVersion.builder()
                .plan(saved)
                .price(price)
                .currency(currency == null ? "NGN" : currency)
                .effectiveFrom(effectiveFrom == null ? LocalDateTime.now() : effectiveFrom)
                .build();
        versionRepository.save(version);

        if (features != null) {
            features.forEach(f -> {
                f.setPlan(saved);
                featureRepository.save(f);
            });
        }
        return saved;
    }

    @Transactional
    public PlanVersion addVersion(UUID planId, BigDecimal price, String currency, LocalDateTime effectiveFrom) {
        BillingPlan plan = planRepository.findById(planId)
                .orElseThrow(() -> new BusinessException("Plan not found", "PLAN_NOT_FOUND"));
        PlanVersion version = PlanVersion.builder()
                .plan(plan)
                .price(price)
                .currency(currency == null ? "NGN" : currency)
                .effectiveFrom(effectiveFrom == null ? LocalDateTime.now() : effectiveFrom)
                .build();
        return versionRepository.save(version);
    }

    @Transactional
    public void setFeatures(UUID planId, List<PlanFeature> features) {
        BillingPlan plan = planRepository.findById(planId)
                .orElseThrow(() -> new BusinessException("Plan not found", "PLAN_NOT_FOUND"));
        List<PlanFeature> existing = featureRepository.findAllByPlanId(planId);
        featureRepository.deleteAll(existing);
        featureRepository.flush();
        features.forEach(f -> {
            f.setId(null);
            f.setPlan(plan);
            featureRepository.save(f);
        });
    }

    @Transactional
    public BillingPlan setStatus(UUID planId, PlanStatus status) {
        BillingPlan plan = planRepository.findById(planId)
                .orElseThrow(() -> new BusinessException("Plan not found", "PLAN_NOT_FOUND"));
        plan.setStatus(status);
        if (status == PlanStatus.ARCHIVED) {
            plan.setArchivedAt(LocalDateTime.now());
        } else {
            plan.setArchivedAt(null);
        }
        return planRepository.save(plan);
    }

    /**
     * Hard delete only when no subscription has ever referenced the plan (§5).
     * Otherwise use deprecate/archive.
     */
    @Transactional
    public void deletePlan(UUID planId) {
        boolean referenced = subscriptionRepository.findAll().stream()
                .anyMatch(s -> s.getPlan().getId().equals(planId)
                        || (s.getPendingPlan() != null && s.getPendingPlan().getId().equals(planId)));
        if (referenced) {
            throw new BusinessException(
                    "Plan has subscription history — deprecate or archive instead", "PLAN_IN_USE");
        }
        planRepository.deleteById(planId);
    }

    @Transactional(readOnly = true)
    public PlanVersion currentVersion(UUID planId) {
        return versionRepository.findFirstByPlanIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                        planId, LocalDateTime.now())
                .orElseThrow(() -> new BusinessException("Plan has no priced version", "PLAN_VERSION_NOT_FOUND"));
    }
}
