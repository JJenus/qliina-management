package com.jjenus.qliina_management.billing.service;

import com.jjenus.qliina_management.billing.model.*;
import com.jjenus.qliina_management.billing.repository.BillingInvoiceRepository;
import com.jjenus.qliina_management.billing.repository.BillingPaymentMethodRepository;
import com.jjenus.qliina_management.billing.repository.PlanVersionRepository;
import com.jjenus.qliina_management.billing.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * The recurring-billing engine: renewal, trial conversion/expiry, deferred
 * downgrade application, and period-end cancellations (MD §6, §7).
 *
 * <p>Fault tolerance: discovery uses {@code FOR UPDATE SKIP LOCKED} so multiple
 * app instances split the work; each item is then re-locked FOR UPDATE and
 * re-validated, and every invoice/payment is idempotent, so a crashed or
 * duplicated sweep can never double-bill.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BillingCycleService {

    private final SubscriptionRepository subscriptionRepository;
    private final BillingInvoiceRepository invoiceRepository;
    private final BillingPaymentMethodRepository paymentMethodRepository;
    private final PlanVersionRepository versionRepository;
    private final InvoiceService invoiceService;
    private final BillingPaymentService paymentService;
    private final SubscriptionService subscriptionService;

    // ---------------------------------------------------------------------
    // Renewal sweep
    // ---------------------------------------------------------------------

    @Transactional
    public boolean renew(UUID subscriptionId) {
        Subscription sub = subscriptionRepository.findByIdForUpdate(subscriptionId).orElse(null);
        if (sub == null || sub.getStatus() != SubscriptionStatus.ACTIVE
                || sub.isCancelAtPeriodEnd()
                || sub.getCurrentPeriodEnd() == null
                || sub.getCurrentPeriodEnd().isAfter(LocalDateTime.now())) {
            return false; // already renewed/canceled by a concurrent sweep
        }
        LocalDateTime now = LocalDateTime.now();
        applyPendingDowngrade(sub, now);
        LocalDateTime periodStart = sub.getCurrentPeriodEnd();
        LocalDateTime periodEnd = periodStart.plusMonths(1);

        String idem = "renewal:" + sub.getId() + ":" + periodStart;
        BillingInvoice invoice = invoiceRepository.findByIdempotencyKey(idem).orElseGet(() ->
                invoiceService.createInvoice(sub.getId(), idem, now, periodEnd,
                        List.of(subscriptionLineItem(sub, periodStart, periodEnd))));

        return switch (invoice.getStatus()) {
            case PAID -> { advancePeriod(sub, periodStart, periodEnd, now); yield true; }
            case OPEN -> {
                if (invoice.getAmount().signum() == 0) {
                    invoice.setStatus(InvoiceStatus.PAID);
                    invoiceRepository.save(invoice);
                    advancePeriod(sub, periodStart, periodEnd, now);
                    yield true;
                }
                BillingPaymentService.PaymentResult r = paymentService.attemptInvoicePayment(
                        invoice.getId(), null, "renewal-pay:" + idem);
                if (r.approved()) {
                    advancePeriod(sub, periodStart, periodEnd, now);
                    yield true;
                }
                failToPastDue(sub, now);
                yield false;
            }
            case FAILED -> {
                failToPastDue(sub, now);
                yield false;
            }
            default -> false;
        };
    }

    /** Applies a deferred downgrade ({@code pending_plan_id}) at renewal (§7). */
    private void applyPendingDowngrade(Subscription sub, LocalDateTime now) {
        if (sub.getPendingPlan() == null) return;
        BillingPlan target = sub.getPendingPlan();
        PlanVersion version = versionRepository.findFirstByPlanOrderByEffectiveFromDesc(target)
                .orElseThrow(() -> new IllegalStateException("Pending plan has no version: " + target.getName()));
        sub.setPlan(target);
        sub.setPlanVersion(version);
        sub.setPendingPlan(null);
        sub.setPendingChangeAt(null);
        subscriptionService.transition(sub, sub.getStatus(), "deferred downgrade to " + target.getName() + " applied");
    }

    private void advancePeriod(Subscription sub, LocalDateTime periodStart, LocalDateTime periodEnd, LocalDateTime now) {
        sub.setCurrentPeriodStart(periodStart);
        sub.setCurrentPeriodEnd(periodEnd);
        sub.setRetryCount(0);
        sub.setNextRetryAt(null);
        subscriptionService.transition(sub, SubscriptionStatus.ACTIVE, "renewal billed");
    }

    private void failToPastDue(Subscription sub, LocalDateTime now) {
        subscriptionService.transition(sub, SubscriptionStatus.PAST_DUE, "renewal payment failed");
        sub.setRetryCount(1);
        sub.setNextRetryAt(now.plusDays(1));
    }

    // ---------------------------------------------------------------------
    // Trial expiry sweep (§6)
    // ---------------------------------------------------------------------

    @Transactional
    public void processExpiredTrial(UUID subscriptionId) {
        Subscription sub = subscriptionRepository.findByIdForUpdate(subscriptionId).orElse(null);
        if (sub == null || sub.getStatus() != SubscriptionStatus.TRIALING
                || sub.getTrialEndsAt() == null
                || sub.getTrialEndsAt().isAfter(LocalDateTime.now())) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        boolean hasPaymentMethod = !paymentMethodRepository
                .findAllByBusinessIdOrderByIsDefaultDescCreatedAtAsc(sub.getBusinessId()).isEmpty();
        if (hasPaymentMethod) {
            subscriptionService.transition(sub, SubscriptionStatus.ACTIVE, "trial converted");
            sub.setCurrentPeriodStart(now);
            sub.setCurrentPeriodEnd(now.plusMonths(1));
            sub.setTrialEndsAt(null);
        } else {
            subscriptionService.transition(sub, SubscriptionStatus.CANCELED,
                    "trial expired without a payment method");
        }
    }

    // ---------------------------------------------------------------------
    // Period-end cancellation sweep (§6)
    // ---------------------------------------------------------------------

    @Transactional
    public void finalizeCancellation(UUID subscriptionId) {
        Subscription sub = subscriptionRepository.findByIdForUpdate(subscriptionId).orElse(null);
        if (sub == null || sub.getStatus() != SubscriptionStatus.ACTIVE
                || !sub.isCancelAtPeriodEnd()
                || sub.getCurrentPeriodEnd() == null
                || sub.getCurrentPeriodEnd().isAfter(LocalDateTime.now())) {
            return;
        }
        subscriptionService.transition(sub, SubscriptionStatus.CANCELED,
                "cancel at period end finalized");
    }

    // ---------------------------------------------------------------------
    // Shared line-item builder
    // ---------------------------------------------------------------------

    private InvoiceService.LineItem subscriptionLineItem(Subscription sub,
                                                         LocalDateTime periodStart, LocalDateTime periodEnd) {
        return new InvoiceService.LineItem(
                LineItemType.SUBSCRIPTION,
                sub.getPlan().getName() + " subscription",
                sub.getPlanVersion().getPrice(), periodStart, periodEnd);
    }
}
