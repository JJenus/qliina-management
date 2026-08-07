package com.jjenus.qliina_management.billing.service;

import com.jjenus.qliina_management.billing.model.BillingPlan;
import com.jjenus.qliina_management.billing.model.PlanStatus;
import com.jjenus.qliina_management.billing.model.PlanVersion;
import com.jjenus.qliina_management.billing.model.Subscription;
import com.jjenus.qliina_management.billing.model.SubscriptionStatus;
import com.jjenus.qliina_management.billing.repository.BillingPlanRepository;
import com.jjenus.qliina_management.billing.repository.PlanVersionRepository;
import com.jjenus.qliina_management.billing.repository.SubscriptionRepository;
import com.jjenus.qliina_management.common.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Subscription lifecycle orchestration — the state machine from MD §6.
 *
 * <pre>
 * trialing --converts-----------------------------------> active
 * trialing --trial expires, no payment------------------> canceled
 * active   --payment fails------------------------------> past_due
 * past_due --dunning retry succeeds---------------------> active
 * past_due --dunning exhausted--------------------------> canceled
 * active   --cancel (immediate or at period end)--------> canceled
 * </pre>
 *
 * {@code canceled} is terminal; every transition is validated and appended to
 * {@code billing_subscription_events}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private static final Map<SubscriptionStatus, Set<SubscriptionStatus>> ALLOWED_TRANSITIONS = Map.of(
            SubscriptionStatus.TRIALING, Set.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.CANCELED),
            SubscriptionStatus.ACTIVE, Set.of(SubscriptionStatus.PAST_DUE, SubscriptionStatus.CANCELED),
            SubscriptionStatus.PAST_DUE, Set.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.CANCELED),
            SubscriptionStatus.CANCELED, Set.of()
    );

    private final SubscriptionRepository subscriptionRepository;
    private final BillingPlanRepository planRepository;
    private final PlanVersionRepository versionRepository;
    private final SubscriptionEventService eventService;
    private final ProrationService prorationService;
    private final CouponService couponService;

    // ---------------------------------------------------------------------
    // Lookup
    // ---------------------------------------------------------------------

    public Subscription getLiveSubscription(UUID businessId) {
        return subscriptionRepository.findByBusinessIdAndStatusNot(businessId, SubscriptionStatus.CANCELED)
                .orElseThrow(() -> new BusinessException("No active subscription", "SUBSCRIPTION_NOT_FOUND"));
    }

    public Subscription getLatestSubscription(UUID businessId) {
        return subscriptionRepository.findTopByBusinessIdOrderByCreatedAtDesc(businessId)
                .orElseThrow(() -> new BusinessException("No subscription found", "SUBSCRIPTION_NOT_FOUND"));
    }

    // ---------------------------------------------------------------------
    // Creation / reactivation
    // ---------------------------------------------------------------------

    /**
     * Creates a trial subscription for a freshly registered business.
     * Guarantees at most one live subscription per business.
     */
    @Transactional
    public Subscription createSubscription(UUID businessId, UUID planId, UUID planVersionId,
                                           LocalDateTime trialEndsAt, String couponCode) {
        subscriptionRepository.findByBusinessIdAndStatusNot(businessId, SubscriptionStatus.CANCELED)
                .ifPresent(existing -> {
                    throw new BusinessException("Business already has an active subscription", "SUBSCRIPTION_EXISTS");
                });
        BillingPlan plan = planRepository.findById(planId)
                .orElseThrow(() -> new BusinessException("Plan not found", "PLAN_NOT_FOUND"));
        PlanVersion version = planVersionId != null
                ? versionRepository.findById(planVersionId)
                        .orElseThrow(() -> new BusinessException("Plan version not found", "PLAN_VERSION_NOT_FOUND"))
                : currentVersion(plan);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime trialEnd = trialEndsAt != null ? trialEndsAt : now.plusDays(30);
        Subscription sub = Subscription.builder()
                .businessId(businessId)
                .plan(plan)
                .planVersion(version)
                .status(SubscriptionStatus.TRIALING)
                .currentPeriodStart(now)
                .currentPeriodEnd(trialEnd)
                .trialEndsAt(trialEnd)
                .cancelAtPeriodEnd(false)
                .retryCount(0)
                .build();
        Subscription saved = subscriptionRepository.save(sub);
        eventService.record(saved.getId(), null, SubscriptionStatus.TRIALING,
                "subscription created on plan " + plan.getName());

        if (couponCode != null && !couponCode.isBlank()) {
            couponService.redeem(businessId, saved.getId(), couponCode);
        }
        return saved;
    }

    /** Reactivation after cancellation — a brand-new subscription (MD §6). */
    @Transactional
    public Subscription reactivate(UUID businessId, String planName) {
        subscriptionRepository.findByBusinessIdAndStatusNot(businessId, SubscriptionStatus.CANCELED)
                .ifPresent(existing -> {
                    throw new BusinessException("Business already has an active subscription", "SUBSCRIPTION_EXISTS");
                });
        BillingPlan plan = resolvePlan(planName);
        PlanVersion version = currentVersion(plan);
        LocalDateTime now = LocalDateTime.now();
        Subscription sub = Subscription.builder()
                .businessId(businessId)
                .plan(plan)
                .planVersion(version)
                .status(SubscriptionStatus.ACTIVE)
                .currentPeriodStart(now)
                .currentPeriodEnd(now.plusMonths(1))
                .cancelAtPeriodEnd(false)
                .retryCount(0)
                .build();
        Subscription saved = subscriptionRepository.save(sub);
        eventService.record(saved.getId(), null, SubscriptionStatus.ACTIVE,
                "reactivated on plan " + plan.getName());
        return saved;
    }

    // ---------------------------------------------------------------------
    // Plan changes (§7)
    // ---------------------------------------------------------------------

    @Transactional
    public Subscription changePlan(UUID businessId, String planName) {
        Subscription sub = getLiveSubscription(businessId);
        BillingPlan target = resolvePlan(planName);
        if (sub.getPlan().getId().equals(target.getId())) {
            throw new BusinessException("Business is already on plan '" + planName + "'", "SAME_PLAN");
        }
        if (target.getStatus() != PlanStatus.ACTIVE) {
            throw new BusinessException("Plan '" + planName + "' is not available", "PLAN_NOT_AVAILABLE");
        }
        PlanVersion targetVersion = currentVersion(target);
        BigDecimal targetPrice = targetVersion.getPrice();
        BigDecimal currentPrice = sub.getPlanVersion().getPrice();

        if (targetPrice.compareTo(currentPrice) > 0) {
            // Upgrade — immediate proration (§7).
            prorationService.applyUpgrade(sub, target, targetVersion);
            return subscriptionRepository.findById(sub.getId())
                    .orElseThrow(() -> new BusinessException("Subscription not found", "SUBSCRIPTION_NOT_FOUND"));
        }
        // Downgrade (or same price) — deferred to next renewal (§7).
        sub.setPendingPlan(target);
        sub.setPendingChangeAt(LocalDateTime.now());
        Subscription saved = subscriptionRepository.save(sub);
        eventService.record(saved.getId(), saved.getStatus(), saved.getStatus(),
                "downgrade to " + target.getName() + " scheduled at period end");
        return saved;
    }

    // ---------------------------------------------------------------------
    // Cancellation (§6)
    // ---------------------------------------------------------------------

    @Transactional
    public Subscription cancel(UUID businessId, boolean atPeriodEnd) {
        Subscription sub = getLiveSubscription(businessId);
        if (atPeriodEnd) {
            sub.setCancelAtPeriodEnd(true);
            return subscriptionRepository.save(sub);
        }
        transition(sub, SubscriptionStatus.CANCELED, "user cancelled (immediate)");
        return subscriptionRepository.save(sub);
    }

    // ---------------------------------------------------------------------
    // State machine
    // ---------------------------------------------------------------------

    /**
     * Validates and applies a status transition, appending to the event ledger.
     * {@code canceled} never transitions back — reactivation creates a new row.
     * A same-status transition is allowed as an annotated "note" (e.g. renewal
     * billed, plan changed) for the event ledger.
     */
    @Transactional
    public void transition(Subscription sub, SubscriptionStatus to, String reason) {
        SubscriptionStatus from = sub.getStatus();
        if (from != to && !ALLOWED_TRANSITIONS.getOrDefault(from, Set.of()).contains(to)) {
            throw new BusinessException(
                    "Illegal subscription transition " + from + " -> " + to, "ILLEGAL_STATE_TRANSITION");
        }
        sub.setStatus(to);
        subscriptionRepository.save(sub);
        eventService.record(sub.getId(), from, to, reason);
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private BillingPlan resolvePlan(String planName) {
        if (planName == null || planName.isBlank()) {
            throw new BusinessException("'plan' field is required", "MISSING_PLAN", "plan");
        }
        return planRepository.findAll().stream()
                .filter(p -> p.getName().equalsIgnoreCase(planName))
                .findFirst()
                .orElseThrow(() -> new BusinessException("Invalid plan '" + planName + "'", "INVALID_PLAN", "plan"));
    }

    private PlanVersion currentVersion(BillingPlan plan) {
        return versionRepository.findFirstByPlanOrderByEffectiveFromDesc(plan)
                .orElseThrow(() -> new BusinessException("Plan has no priced version", "PLAN_VERSION_NOT_FOUND"));
    }
}
