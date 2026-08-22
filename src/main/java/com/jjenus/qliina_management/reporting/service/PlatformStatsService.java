package com.jjenus.qliina_management.reporting.service;

import com.jjenus.qliina_management.billing.model.BillingPayment;
import com.jjenus.qliina_management.billing.model.PaymentStatus;
import com.jjenus.qliina_management.billing.model.Subscription;
import com.jjenus.qliina_management.billing.model.SubscriptionStatus;
import com.jjenus.qliina_management.billing.repository.BillingInvoiceRepository;
import com.jjenus.qliina_management.billing.repository.BillingPaymentRepository;
import com.jjenus.qliina_management.billing.repository.SubscriptionEventRepository;
import com.jjenus.qliina_management.billing.repository.SubscriptionRepository;
import com.jjenus.qliina_management.business.model.Business;
import com.jjenus.qliina_management.business.repository.BusinessRepository;
import com.jjenus.qliina_management.reporting.dto.PlatformStats;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Cross-tenant platform analytics for the admin dashboard. Read-only; every
 * figure is computed from existing billing/business tables — no new
 * instrumentation required.
 */
@Service
@RequiredArgsConstructor
public class PlatformStatsService {

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy-MM");

    private final BusinessRepository businessRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final BillingInvoiceRepository invoiceRepository;
    private final BillingPaymentRepository paymentRepository;
    private final SubscriptionEventRepository subscriptionEventRepository;

    // ---------------------------------------------------------------------
    // Overview
    // ---------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PlatformStats.Overview overview() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime d7 = now.minusDays(7);
        LocalDateTime d30 = now.minusDays(30);

        Map<String, Long> byStatus = new HashMap<>();
        for (Business.Status s : Business.Status.values()) {
            byStatus.put(s.name(), businessRepository.countByStatus(s));
        }

        long active = subscriptionRepository.countByStatus(SubscriptionStatus.ACTIVE);
        BigDecimal mrr = subscriptionRepository.sumActivePrice();

        long trialsStarted30d = subscriptionEventRepository
                .countByToStatusAndOccurredAtAfter(SubscriptionStatus.TRIALING, d30);
        long conversions30d = subscriptionEventRepository
                .countByFromStatusAndToStatusAndOccurredAtAfter(SubscriptionStatus.TRIALING, SubscriptionStatus.ACTIVE, d30);
        long cancellations30d = subscriptionEventRepository
                .countByToStatusAndOccurredAtAfter(SubscriptionStatus.CANCELED, d30);

        return PlatformStats.Overview.builder()
                .totalBusinesses(businessRepository.count())
                .businessesByStatus(byStatus)
                .newSignups7d(businessRepository.countByCreatedAtAfter(d7))
                .newSignups30d(businessRepository.countByCreatedAtAfter(d30))
                .activeSubscriptions(active)
                .trialingSubscriptions(subscriptionRepository.countByStatus(SubscriptionStatus.TRIALING))
                .pastDueSubscriptions(subscriptionRepository.countByStatus(SubscriptionStatus.PAST_DUE))
                .canceledSubscriptions(subscriptionRepository.countByStatus(SubscriptionStatus.CANCELED))
                .mrr(mrr)
                .arr(mrr.multiply(BigDecimal.valueOf(12)))
                .arpu(active > 0 ? mrr.divide(BigDecimal.valueOf(active), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO)
                .trialConversionRate30d(rate(conversions30d, trialsStarted30d))
                .churnRate30d(rate(cancellations30d, active + cancellations30d))
                .trialsExpiring7d(subscriptionRepository.countByStatusAndTrialEndsAtBetween(
                        SubscriptionStatus.TRIALING, now, now.plusDays(7)))
                .pendingCancellations(subscriptionRepository.countByStatusAndCancelAtPeriodEndTrue(SubscriptionStatus.ACTIVE))
                .generatedAt(now)
                .build();
    }

    // ---------------------------------------------------------------------
    // Revenue
    // ---------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PlatformStats.Revenue revenue() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime d30 = now.minusDays(30);
        LocalDateTime d6mo = now.minusMonths(6).withDayOfMonth(1);

        List<BillingPayment> approved = paymentRepository.findApprovedSince(d6mo);
        Map<String, BigDecimal> byMonth = new HashMap<>();
        for (BillingPayment p : approved) {
            if (p.getPaidAt() == null) continue;
            byMonth.merge(MONTH.format(p.getPaidAt()), p.getAmount(), BigDecimal::add);
        }
        List<PlatformStats.MonthlyPoint> trend = byMonth.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> PlatformStats.MonthlyPoint.builder().month(e.getKey()).amount(e.getValue()).build())
                .toList();

        List<PlatformStats.NamedAmount> byPlan = subscriptionRepository.sumActivePriceByPlan().stream()
                .map(row -> PlatformStats.NamedAmount.builder()
                        .name((String) row[0])
                        .amount((BigDecimal) row[1])
                        .build())
                .toList();

        return PlatformStats.Revenue.builder()
                .collected30d(paymentRepository.sumApprovedSince(d30))
                .invoiced30d(invoiceRepository.sumIssuedSince(d30))
                .outstanding(invoiceRepository.sumOpenAmount())
                .failedPayments30d(paymentRepository.countByStatus(PaymentStatus.FAILED))
                .openInvoices(invoiceRepository.countByStatus(com.jjenus.qliina_management.billing.model.InvoiceStatus.OPEN))
                .trend(trend)
                .byPlan(byPlan)
                .build();
    }

    // ---------------------------------------------------------------------
    // Growth
    // ---------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PlatformStats.Growth growth() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime d30 = now.minusDays(30);
        LocalDateTime d6mo = now.minusMonths(6).withDayOfMonth(1);

        var page = businessRepository.findByCreatedAtAfter(d6mo, PageRequest.of(0, 10_000, Sort.by("createdAt")));
        Map<String, Long> signups = new HashMap<>();
        for (Business b : page) {
            if (b.getCreatedAt() == null) continue;
            signups.merge(MONTH.format(b.getCreatedAt()), 1L, Long::sum);
        }

        return PlatformStats.Growth.builder()
                .signupsByMonth(signups.entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .map(e -> PlatformStats.MonthlyCount.builder().month(e.getKey()).count(e.getValue()).build())
                        .toList())
                .planDistribution(subscriptionRepository.countByPlanGrouped().stream()
                        .map(row -> PlatformStats.NamedCount.builder()
                                .name((String) row[0])
                                .count((Long) row[1])
                                .build())
                        .toList())
                .trialsStarted30d(subscriptionEventRepository
                        .countByToStatusAndOccurredAtAfter(SubscriptionStatus.TRIALING, d30))
                .conversions30d(subscriptionEventRepository
                        .countByFromStatusAndToStatusAndOccurredAtAfter(SubscriptionStatus.TRIALING, SubscriptionStatus.ACTIVE, d30))
                .cancellations30d(subscriptionEventRepository
                        .countByToStatusAndOccurredAtAfter(SubscriptionStatus.CANCELED, d30))
                .build();
    }

    // ---------------------------------------------------------------------
    // Dunning & risk
    // ---------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PlatformStats.Dunning dunning() {
        LocalDateTime now = LocalDateTime.now();

        var pastDuePage = subscriptionRepository.findByStatus(
                SubscriptionStatus.PAST_DUE,
                PageRequest.of(0, 50, Sort.by(Sort.Direction.ASC, "nextRetryAt")));

        var expiringPage = subscriptionRepository.findByStatusAndTrialEndsAtBetween(
                SubscriptionStatus.TRIALING, now, now.plusDays(7),
                PageRequest.of(0, 50, Sort.by(Sort.Direction.ASC, "trialEndsAt")));

        Map<UUID, String> bizNames = businessNameCache(pastDuePage.getContent(), expiringPage.getContent());

        List<PlatformStats.PastDueRow> pastDue = pastDuePage.getContent().stream()
                .map(s -> PlatformStats.PastDueRow.builder()
                        .subscriptionId(s.getId())
                        .businessId(s.getBusinessId())
                        .businessName(bizNames.get(s.getBusinessId()))
                        .planName(s.getPlan() != null ? s.getPlan().getName() : null)
                        .retryCount(s.getRetryCount())
                        .nextRetryAt(s.getNextRetryAt())
                        .amountDue(s.getPlanVersion() != null ? s.getPlanVersion().getPrice() : BigDecimal.ZERO)
                        .build())
                .toList();

        List<PlatformStats.ExpiringTrialRow> expiring = expiringPage.getContent().stream()
                .map(s -> PlatformStats.ExpiringTrialRow.builder()
                        .subscriptionId(s.getId())
                        .businessId(s.getBusinessId())
                        .businessName(bizNames.get(s.getBusinessId()))
                        .planName(s.getPlan() != null ? s.getPlan().getName() : null)
                        .trialEndsAt(s.getTrialEndsAt())
                        .build())
                .toList();

        return PlatformStats.Dunning.builder()
                .pastDueCount(subscriptionRepository.countByStatus(SubscriptionStatus.PAST_DUE))
                .failedPayments30d(paymentRepository.countByStatus(PaymentStatus.FAILED))
                .trialsExpiring7d(subscriptionRepository.countByStatusAndTrialEndsAtBetween(
                        SubscriptionStatus.TRIALING, now, now.plusDays(7)))
                .pendingCancellations(subscriptionRepository.countByStatusAndCancelAtPeriodEndTrue(SubscriptionStatus.ACTIVE))
                .pastDue(pastDue)
                .expiringTrials(expiring)
                .build();
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private Map<UUID, String> businessNameCache(List<Subscription>... groups) {
        Map<UUID, String> names = new HashMap<>();
        for (List<Subscription> group : groups) {
            Set<UUID> ids = group.stream().map(Subscription::getBusinessId).collect(Collectors.toSet());
            businessRepository.findAllById(ids).forEach(b -> names.put(b.getId(), b.getName()));
        }
        return names;
    }

    private double rate(long numerator, long denominator) {
        return denominator <= 0 ? 0 : Math.round((numerator * 10000.0) / denominator) / 100.0;
    }
}
