package com.jjenus.qliina_management.reporting.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Read-models for the platform-admin analytics endpoints
 * (/api/v1/admin/stats/**). All figures are cross-tenant aggregates.
 */
public final class PlatformStats {

    private PlatformStats() {}

    @Builder
    public record Overview(
            long totalBusinesses,
            Map<String, Long> businessesByStatus,
            long newSignups7d,
            long newSignups30d,
            long activeSubscriptions,
            long trialingSubscriptions,
            long pastDueSubscriptions,
            long canceledSubscriptions,
            BigDecimal mrr,
            BigDecimal arr,
            BigDecimal arpu,
            double trialConversionRate30d,
            double churnRate30d,
            long trialsExpiring7d,
            long pendingCancellations,
            LocalDateTime generatedAt
    ) {}

    @Builder
    public record Revenue(
            BigDecimal collected30d,
            BigDecimal invoiced30d,
            BigDecimal outstanding,
            long failedPayments30d,
            long openInvoices,
            List<MonthlyPoint> trend,
            List<NamedAmount> byPlan
    ) {}

    @Builder
    public record MonthlyPoint(String month, BigDecimal amount) {}

    @Builder
    public record NamedAmount(String name, BigDecimal amount) {}

    @Builder
    public record Growth(
            List<MonthlyCount> signupsByMonth,
            List<NamedCount> planDistribution,
            long trialsStarted30d,
            long conversions30d,
            long cancellations30d
    ) {}

    @Builder
    public record MonthlyCount(String month, long count) {}

    @Builder
    public record NamedCount(String name, long count) {}

    @Builder
    public record Dunning(
            long pastDueCount,
            long failedPayments30d,
            long trialsExpiring7d,
            long pendingCancellations,
            List<PastDueRow> pastDue,
            List<ExpiringTrialRow> expiringTrials
    ) {}

    @Builder
    public record PastDueRow(
            UUID subscriptionId,
            UUID businessId,
            String businessName,
            String planName,
            int retryCount,
            LocalDateTime nextRetryAt,
            BigDecimal amountDue
    ) {}

    @Builder
    public record ExpiringTrialRow(
            UUID subscriptionId,
            UUID businessId,
            String businessName,
            String planName,
            LocalDateTime trialEndsAt
    ) {}
}
