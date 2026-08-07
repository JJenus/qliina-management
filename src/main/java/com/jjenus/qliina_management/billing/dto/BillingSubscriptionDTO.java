package com.jjenus.qliina_management.billing.dto;

import com.jjenus.qliina_management.billing.model.SubscriptionStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Current billing state for a business.
 */
public record BillingSubscriptionDTO(
        UUID id,
        UUID businessId,
        UUID planId,
        String planName,
        SubscriptionStatus status,
        BigDecimal price,
        String currency,
        LocalDateTime currentPeriodStart,
        LocalDateTime currentPeriodEnd,
        LocalDateTime trialEndsAt,
        boolean cancelAtPeriodEnd,
        int retryCount,
        LocalDateTime nextRetryAt,
        String pendingPlanName,
        LocalDateTime pendingChangeAt,
        String appliedCouponCode
) {
}
