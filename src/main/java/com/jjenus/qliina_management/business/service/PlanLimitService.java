package com.jjenus.qliina_management.business.service;

import com.jjenus.qliina_management.business.dto.PlanUsageDTO;
import com.jjenus.qliina_management.business.model.Business;
import com.jjenus.qliina_management.business.model.SubscriptionPlan;
import com.jjenus.qliina_management.business.repository.BusinessRepository;
import com.jjenus.qliina_management.business.repository.ShopRepository;
import com.jjenus.qliina_management.business.repository.SubscriptionPlanRepository;
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
 * <p>
 * Hard limits throw {@link BusinessException} with code PLAN_LIMIT_EXCEEDED.
 * Feature gates throw {@link BusinessException} with code FEATURE_NOT_AVAILABLE.
 * Warning notifications are sent asynchronously when usage reaches 80%.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlanLimitService {

    private static final double WARNING_THRESHOLD = 0.8;

    private final SubscriptionPlanRepository planRepository;
    private final BusinessRepository         businessRepository;
    private final ShopRepository             shopRepository;
    private final UserRepository             userRepository;
    private final OrderRepository            orderRepository;
    private final NotificationRepository     notificationRepository;

    // -------------------------------------------------------------------------
    // Plan lookup
    // -------------------------------------------------------------------------

    public SubscriptionPlan getPlan(UUID businessId) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new BusinessException("Business not found", "BUSINESS_NOT_FOUND"));
        String tierName = business.getPlan().name();
        return planRepository.findByTier(tierName)
                .orElseGet(() -> planRepository.findByTier("FREE")
                        .orElseThrow(() -> new BusinessException("Subscription plan not configured", "PLAN_NOT_FOUND")));
    }

    // -------------------------------------------------------------------------
    // Hard-limit enforcement
    // -------------------------------------------------------------------------

    /** Called before creating a new shop. Throws if at or above hard limit. */
    public void enforceShopLimit(UUID businessId) {
        SubscriptionPlan plan = getPlan(businessId);
        if (plan.getMaxShops() < 0) return; // unlimited
        long current = shopRepository.countActiveByBusinessId(businessId);
        if (current >= plan.getMaxShops()) {
            throw new BusinessException(
                    "Your plan allows a maximum of " + plan.getMaxShops() + " shops. Upgrade to add more.",
                    "PLAN_LIMIT_EXCEEDED");
        }
        // Warn at 80%
        double ratio = (double) current / plan.getMaxShops();
        if (ratio >= WARNING_THRESHOLD) {
            sendPlanWarning(businessId,
                    "Shop limit warning: you are using " + current + " of " + plan.getMaxShops() + " shops.",
                    ratio);
        }
    }

    /** Called before creating a new user. Throws if at or above hard limit. */
    public void enforceUserLimit(UUID businessId) {
        SubscriptionPlan plan = getPlan(businessId);
        if (plan.getMaxUsers() < 0) return;
        long current = userRepository.countByBusinessIdAndEnabledTrue(businessId);
        if (current >= plan.getMaxUsers()) {
            throw new BusinessException(
                    "Your plan allows a maximum of " + plan.getMaxUsers() + " users. Upgrade to add more.",
                    "PLAN_LIMIT_EXCEEDED");
        }
        double ratio = (double) current / plan.getMaxUsers();
        if (ratio >= WARNING_THRESHOLD) {
            sendPlanWarning(businessId,
                    "User limit warning: you are using " + current + " of " + plan.getMaxUsers() + " users.",
                    ratio);
        }
    }

    /** Called before creating a new order. Throws if at or above monthly hard limit. */
    public void enforceOrderLimit(UUID businessId) {
        SubscriptionPlan plan = getPlan(businessId);
        if (plan.getMaxOrdersPerMonth() < 0) return;
        LocalDateTime monthStart = LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        long current = orderRepository.countOrdersByDateRange(businessId, null, monthStart, LocalDateTime.now());
        if (current >= plan.getMaxOrdersPerMonth()) {
            throw new BusinessException(
                    "Monthly order limit reached (" + plan.getMaxOrdersPerMonth() + "). Upgrade for unlimited orders.",
                    "PLAN_LIMIT_EXCEEDED");
        }
        double ratio = (double) current / plan.getMaxOrdersPerMonth();
        if (ratio >= WARNING_THRESHOLD) {
            sendPlanWarning(businessId,
                    "Order limit warning: " + current + " of " + plan.getMaxOrdersPerMonth() + " orders used this month.",
                    ratio);
        }
    }

    /**
     * Throws FEATURE_NOT_AVAILABLE if the feature flag is false for the business's plan.
     *
     * @param featureName one of: advancedAnalytics, exportReports, loyaltyProgram,
     *                    qualityControl, apiAccess, prioritySupport, customBranding, multiShopReporting
     */
    public void enforceFeature(UUID businessId, String featureName) {
        SubscriptionPlan plan = getPlan(businessId);
        boolean allowed = switch (featureName) {
            case "advancedAnalytics"  -> plan.isAdvancedAnalytics();
            case "exportReports"      -> plan.isExportReports();
            case "loyaltyProgram"     -> plan.isLoyaltyProgram();
            case "qualityControl"     -> plan.isQualityControl();
            case "apiAccess"          -> plan.isApiAccess();
            case "prioritySupport"    -> plan.isPrioritySupport();
            case "customBranding"     -> plan.isCustomBranding();
            case "multiShopReporting" -> plan.isMultiShopReporting();
            default -> {
                log.warn("Unknown feature name checked: {}", featureName);
                yield true; // unknown features are permitted by default
            }
        };
        if (!allowed) {
            throw new BusinessException(
                    "Feature '" + featureName + "' is not available on your current plan. Upgrade to access it.",
                    "FEATURE_NOT_AVAILABLE");
        }
    }

    // -------------------------------------------------------------------------
    // Usage ratio helpers (0.0–1.0, or -1 for unlimited)
    // -------------------------------------------------------------------------

    public double shopUsageRatio(UUID businessId) {
        SubscriptionPlan plan = getPlan(businessId);
        if (plan.getMaxShops() < 0) return -1;
        return (double) shopRepository.countActiveByBusinessId(businessId) / plan.getMaxShops();
    }

    public double userUsageRatio(UUID businessId) {
        SubscriptionPlan plan = getPlan(businessId);
        if (plan.getMaxUsers() < 0) return -1;
        return (double) userRepository.countByBusinessIdAndEnabledTrue(businessId) / plan.getMaxUsers();
    }

    public double orderUsageRatio(UUID businessId) {
        SubscriptionPlan plan = getPlan(businessId);
        if (plan.getMaxOrdersPerMonth() < 0) return -1;
        LocalDateTime monthStart = LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        long current = orderRepository.countOrdersByDateRange(businessId, null, monthStart, LocalDateTime.now());
        return (double) current / plan.getMaxOrdersPerMonth();
    }

    // -------------------------------------------------------------------------
    // Full usage summary
    // -------------------------------------------------------------------------

    public PlanUsageDTO getUsageSummary(UUID businessId) {
        SubscriptionPlan plan = getPlan(businessId);
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new BusinessException("Business not found", "BUSINESS_NOT_FOUND"));

        long activeShops   = shopRepository.countActiveByBusinessId(businessId);
        long activeUsers   = userRepository.countByBusinessIdAndEnabledTrue(businessId);
        LocalDateTime monthStart = LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        long ordersThisMonth = orderRepository.countOrdersByDateRange(businessId, null, monthStart, LocalDateTime.now());

        int daysLeftInTrial = -1;
        boolean trialExpired = false;
        if (business.getTrialEndsAt() != null) {
            LocalDateTime now = LocalDateTime.now();
            trialExpired = now.isAfter(business.getTrialEndsAt());
            daysLeftInTrial = trialExpired ? 0 :
                    (int) java.time.Duration.between(now, business.getTrialEndsAt()).toDays();
        }

        return PlanUsageDTO.builder()
                .planName(plan.getName())
                .tier(plan.getTier())
                .status(business.getStatus().name())
                .trialEndsAt(business.getTrialEndsAt())
                .isTrialExpired(trialExpired)
                .daysLeftInTrial(daysLeftInTrial)
                .activeShops(activeShops)
                .maxShops(plan.getMaxShops())
                .shopsUsageRatio(plan.getMaxShops() < 0 ? -1 : (double) activeShops / plan.getMaxShops())
                .activeUsers(activeUsers)
                .maxUsers(plan.getMaxUsers())
                .usersUsageRatio(plan.getMaxUsers() < 0 ? -1 : (double) activeUsers / plan.getMaxUsers())
                .activeEmployees(activeUsers)           // employees are users with employee roles
                .maxEmployees(plan.getMaxEmployees())
                .employeesUsageRatio(plan.getMaxEmployees() < 0 ? -1 : (double) activeUsers / plan.getMaxEmployees())
                .ordersThisMonth(ordersThisMonth)
                .maxOrdersPerMonth(plan.getMaxOrdersPerMonth())
                .ordersUsageRatio(plan.getMaxOrdersPerMonth() < 0 ? -1 : (double) ordersThisMonth / plan.getMaxOrdersPerMonth())
                .advancedAnalytics(plan.isAdvancedAnalytics())
                .exportReports(plan.isExportReports())
                .loyaltyProgram(plan.isLoyaltyProgram())
                .qualityControl(plan.isQualityControl())
                .apiAccess(plan.isApiAccess())
                .prioritySupport(plan.isPrioritySupport())
                .customBranding(plan.isCustomBranding())
                .multiShopReporting(plan.isMultiShopReporting())
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
