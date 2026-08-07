package com.jjenus.qliina_management.business.service;

import com.jjenus.qliina_management.billing.service.BillingFeatureResolver;
import com.jjenus.qliina_management.billing.service.SubscriptionService;
import com.jjenus.qliina_management.business.dto.PlanUsageDTO;
import com.jjenus.qliina_management.business.model.Business;
import com.jjenus.qliina_management.business.repository.BusinessRepository;
import com.jjenus.qliina_management.business.repository.ShopRepository;
import com.jjenus.qliina_management.common.BusinessException;
import com.jjenus.qliina_management.identity.repository.UserRepository;
import com.jjenus.qliina_management.notification.model.Notification;
import com.jjenus.qliina_management.notification.repository.NotificationRepository;
import com.jjenus.qliina_management.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Enforces subscription plan limits across the platform.
 *
 * <p>Source of truth is the billing schema (MD §2): limits and feature flags are
 * read from plan_features via {@link BillingFeatureResolver} — never from a
 * fixed-column plans table. Hard limits throw {@link BusinessException} with code
 * PLAN_LIMIT_EXCEEDED; feature gates throw FEATURE_NOT_AVAILABLE; warnings are
 * queued asynchronously at 80% usage.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlanLimitService {

    private static final double WARNING_THRESHOLD = 0.8;

    private final BillingFeatureResolver     featureResolver;
    private final SubscriptionService        subscriptionService;
    private final BusinessRepository         businessRepository;
    private final ShopRepository             shopRepository;
    private final UserRepository             userRepository;
    private final OrderRepository            orderRepository;
    private final NotificationRepository     notificationRepository;

    // -------------------------------------------------------------------------
    // Plan identity (falls back to FREE when no live subscription exists)
    // -------------------------------------------------------------------------

    public String planName(UUID businessId) {
        try {
            return subscriptionService.getLiveSubscription(businessId).getPlan().getName();
        } catch (BusinessException e) {
            return "Free";
        }
    }

    public String planTier(UUID businessId) {
        try {
            return subscriptionService.getLiveSubscription(businessId).getPlan().getName().toUpperCase();
        } catch (BusinessException e) {
            return "FREE";
        }
    }

    // -------------------------------------------------------------------------
    // Hard-limit enforcement
    // -------------------------------------------------------------------------

    /** Called before creating a new shop. Throws if at or above hard limit. */
    public void enforceShopLimit(UUID businessId) {
        int maxShops = featureResolver.intValue(businessId, "max_shops", -1);
        if (maxShops < 0) return; // unlimited
        long current = shopRepository.countActiveByBusinessId(businessId);
        if (current >= maxShops) {
            throw new BusinessException(
                    "Your plan allows a maximum of " + maxShops + " shops. Upgrade to add more.",
                    "PLAN_LIMIT_EXCEEDED");
        }
        double ratio = (double) current / maxShops;
        if (ratio >= WARNING_THRESHOLD) {
            sendPlanWarning(businessId,
                    "Shop limit warning: you are using " + current + " of " + maxShops + " shops.",
                    ratio);
        }
    }

    /** Called before creating a new user. Throws if at or above hard limit. */
    public void enforceUserLimit(UUID businessId) {
        int maxUsers = featureResolver.intValue(businessId, "max_users", -1);
        if (maxUsers < 0) return;
        long current = userRepository.countByBusinessIdAndEnabledTrue(businessId);
        if (current >= maxUsers) {
            throw new BusinessException(
                    "Your plan allows a maximum of " + maxUsers + " users. Upgrade to add more.",
                    "PLAN_LIMIT_EXCEEDED");
        }
        double ratio = (double) current / maxUsers;
        if (ratio >= WARNING_THRESHOLD) {
            sendPlanWarning(businessId,
                    "User limit warning: you are using " + current + " of " + maxUsers + " users.",
                    ratio);
        }
    }

    /** Called before creating a new order. Throws if at or above monthly hard limit. */
    public void enforceOrderLimit(UUID businessId) {
        int maxOrders = featureResolver.intValue(businessId, "max_orders_per_month", -1);
        if (maxOrders < 0) return;
        LocalDateTime monthStart = LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        long current = orderRepository.countOrdersByDateRange(businessId, null, monthStart, LocalDateTime.now());
        if (current >= maxOrders) {
            throw new BusinessException(
                    "Monthly order limit reached (" + maxOrders + "). Upgrade for unlimited orders.",
                    "PLAN_LIMIT_EXCEEDED");
        }
        double ratio = (double) current / maxOrders;
        if (ratio >= WARNING_THRESHOLD) {
            sendPlanWarning(businessId,
                    "Order limit warning: " + current + " of " + maxOrders + " orders used this month.",
                    ratio);
        }
    }

    /**
     * Throws FEATURE_NOT_AVAILABLE if the feature flag is disabled for the business's plan.
     */
    public void enforceFeature(UUID businessId, String featureName) {
        if (!featureResolver.boolValue(businessId, featureName, false)) {
            throw new BusinessException(
                    "Feature '" + featureName + "' is not available on your current plan. Upgrade to access it.",
                    "FEATURE_NOT_AVAILABLE");
        }
    }

    // -------------------------------------------------------------------------
    // Usage ratio helpers (0.0–1.0, or -1 for unlimited)
    // -------------------------------------------------------------------------

    public double shopUsageRatio(UUID businessId) {
        int maxShops = featureResolver.intValue(businessId, "max_shops", -1);
        if (maxShops < 0) return -1;
        return (double) shopRepository.countActiveByBusinessId(businessId) / maxShops;
    }

    public double userUsageRatio(UUID businessId) {
        int maxUsers = featureResolver.intValue(businessId, "max_users", -1);
        if (maxUsers < 0) return -1;
        return (double) userRepository.countByBusinessIdAndEnabledTrue(businessId) / maxUsers;
    }

    public double orderUsageRatio(UUID businessId) {
        int maxOrders = featureResolver.intValue(businessId, "max_orders_per_month", -1);
        if (maxOrders < 0) return -1;
        LocalDateTime monthStart = LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        long current = orderRepository.countOrdersByDateRange(businessId, null, monthStart, LocalDateTime.now());
        return (double) current / maxOrders;
    }

    // -------------------------------------------------------------------------
    // Full usage summary
    // -------------------------------------------------------------------------

    public PlanUsageDTO getUsageSummary(UUID businessId) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new BusinessException("Business not found", "BUSINESS_NOT_FOUND"));

        long activeShops = shopRepository.countActiveByBusinessId(businessId);
        long activeUsers = userRepository.countByBusinessIdAndEnabledTrue(businessId);
        LocalDateTime monthStart = LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        long ordersThisMonth = orderRepository.countOrdersByDateRange(businessId, null, monthStart, LocalDateTime.now());

        int maxShops = featureResolver.intValue(businessId, "max_shops", -1);
        int maxUsers = featureResolver.intValue(businessId, "max_users", -1);
        int maxEmployees = featureResolver.intValue(businessId, "max_employees", -1);
        int maxOrders = featureResolver.intValue(businessId, "max_orders_per_month", -1);

        int daysLeftInTrial = -1;
        boolean trialExpired = false;
        if (business.getTrialEndsAt() != null) {
            LocalDateTime now = LocalDateTime.now();
            trialExpired = now.isAfter(business.getTrialEndsAt());
            daysLeftInTrial = trialExpired ? 0 :
                    (int) java.time.Duration.between(now, business.getTrialEndsAt()).toDays();
        }

        return PlanUsageDTO.builder()
                .planName(planName(businessId))
                .tier(planTier(businessId))
                .status(business.getStatus().name())
                .trialEndsAt(business.getTrialEndsAt())
                .isTrialExpired(trialExpired)
                .daysLeftInTrial(daysLeftInTrial)
                .activeShops(activeShops)
                .maxShops(maxShops)
                .shopsUsageRatio(maxShops < 0 ? -1 : (double) activeShops / maxShops)
                .activeUsers(activeUsers)
                .maxUsers(maxUsers)
                .usersUsageRatio(maxUsers < 0 ? -1 : (double) activeUsers / maxUsers)
                .activeEmployees(activeUsers)
                .maxEmployees(maxEmployees)
                .employeesUsageRatio(maxEmployees < 0 ? -1 : (double) activeUsers / maxEmployees)
                .ordersThisMonth(ordersThisMonth)
                .maxOrdersPerMonth(maxOrders)
                .ordersUsageRatio(maxOrders < 0 ? -1 : (double) ordersThisMonth / maxOrders)
                .advancedAnalytics(featureResolver.boolValue(businessId, "advancedAnalytics", false))
                .exportReports(featureResolver.boolValue(businessId, "exportReports", false))
                .loyaltyProgram(featureResolver.boolValue(businessId, "loyaltyProgram", false))
                .qualityControl(featureResolver.boolValue(businessId, "qualityControl", false))
                .apiAccess(featureResolver.boolValue(businessId, "apiAccess", false))
                .prioritySupport(featureResolver.boolValue(businessId, "prioritySupport", false))
                .customBranding(featureResolver.boolValue(businessId, "customBranding", false))
                .multiShopReporting(featureResolver.boolValue(businessId, "multiShopReporting", false))
                .build();
    }

    // -------------------------------------------------------------------------
    // Warning notification (fire-and-forget)
    // -------------------------------------------------------------------------

    @Async
    protected void sendPlanWarning(UUID businessId, String message, double ratio) {
        try {
            Notification n = new Notification();
            n.setBusinessId(businessId);
            n.setTitle("Plan Usage Warning");
            n.setBody(message);
            n.setType(Notification.NotificationType.ALERT);
            n.setChannel(Notification.NotificationChannel.IN_APP);
            n.setPriority(Notification.NotificationPriority.HIGH);
            n.setStatus(Notification.NotificationStatus.PENDING);
            n.setCreatedAt(LocalDateTime.now());
            n.setCreatedBy(UUID.fromString("00000000-0000-0000-0000-000000000000"));
            notificationRepository.save(n);
            log.info("Plan warning queued for businessId={}: {}", businessId, message);
        } catch (Exception e) {
            log.warn("Failed to queue plan warning notification: {}", e.getMessage());
        }
    }
}
