package com.jjenus.qliina_management.billing.service;

import com.jjenus.qliina_management.billing.model.BillingPlan;
import com.jjenus.qliina_management.billing.model.LineItemType;
import com.jjenus.qliina_management.billing.model.PlanVersion;
import com.jjenus.qliina_management.billing.model.Subscription;
import com.jjenus.qliina_management.billing.repository.BillingPaymentMethodRepository;
import com.jjenus.qliina_management.billing.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Proration math for mid-cycle plan changes (MD §7).
 *
 * <p>Upgrades charge immediately: unused value on the old plan is credited and
 * the remaining time on the new plan is charged, netted into one invoice.
 * Downgrades are deferred via {@code pending_plan_id} and applied at renewal.
 * All amounts lock onto plan_version prices — never a live lookup.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProrationService {

    private final SubscriptionRepository subscriptionRepository;
    private final BillingPaymentMethodRepository paymentMethodRepository;
    private final InvoiceService invoiceService;
    private final BillingPaymentService paymentService;
    private final SubscriptionEventService eventService;

    @Transactional
    public void applyUpgrade(Subscription sub, BillingPlan targetPlan, PlanVersion targetVersion) {
        LocalDateTime now = LocalDateTime.now();
        BigDecimal currentPrice = sub.getPlanVersion().getPrice();
        BigDecimal targetPrice = targetVersion.getPrice();
        String oldName = sub.getPlan().getName();
        String newName = targetPlan.getName();

        LocalDateTime periodEnd = sub.getCurrentPeriodEnd() != null
                ? sub.getCurrentPeriodEnd()
                : (sub.getTrialEndsAt() != null ? sub.getTrialEndsAt() : now.plusMonths(1));

        long totalDays = Math.max(1, ChronoUnit.DAYS.between(sub.getCurrentPeriodStart(), periodEnd));
        long daysLeft = Math.max(0, ChronoUnit.DAYS.between(now, periodEnd));
        BigDecimal ratio = BigDecimal.valueOf(daysLeft)
                .divide(BigDecimal.valueOf(totalDays), 6, RoundingMode.HALF_UP);

        BigDecimal unusedValue = currentPrice.multiply(ratio).setScale(2, RoundingMode.HALF_UP);
        BigDecimal remainingCost = targetPrice.multiply(ratio).setScale(2, RoundingMode.HALF_UP);
        BigDecimal net = remainingCost.subtract(unusedValue);

        // Switch plan/version first — the subscription always lands on the new price.
        sub.setPlan(targetPlan);
        sub.setPlanVersion(targetVersion);
        subscriptionRepository.save(sub);
        eventService.record(sub.getId(), sub.getStatus(), sub.getStatus(),
                "plan changed " + oldName + " -> " + newName);

        if (net.compareTo(BigDecimal.ZERO) > 0) {
            String idem = "upgrade:" + sub.getId() + ":" + targetPlan.getId() + ":" + periodEnd;
            boolean hasPaymentMethod = !paymentMethodRepository
                    .findAllByBusinessIdOrderByIsDefaultDescCreatedAtAsc(sub.getBusinessId()).isEmpty();
            InvoiceService.LineItem credit = new InvoiceService.LineItem(
                    LineItemType.PRORATION_CREDIT,
                    "Unused value on " + oldName,
                    unusedValue.negate(), sub.getCurrentPeriodStart(), now);
            InvoiceService.LineItem charge = new InvoiceService.LineItem(
                    LineItemType.PRORATION_CHARGE,
                    "Pro-rated " + newName + " for remaining period",
                    remainingCost, now, periodEnd);
            var invoice = invoiceService.createInvoice(
                    sub.getId(), idem, now, sub.getCurrentPeriodEnd(),
                    List.of(credit, charge));
            if (hasPaymentMethod) {
                paymentService.attemptInvoicePayment(invoice.getId(), null, "upgrade-pay:" + idem);
            } else {
                log.info("Upgrade proration invoice {} left OPEN — no payment method on file", invoice.getInvoiceNumber());
            }
        } else {
            log.info("Plan change {} -> {} produces no additional charge (net={}), skipping invoice",
                    oldName, newName, net);
        }
    }
}
