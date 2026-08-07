package com.jjenus.qliina_management.billing.service;

import com.jjenus.qliina_management.billing.model.*;
import com.jjenus.qliina_management.billing.repository.BillingInvoiceRepository;
import com.jjenus.qliina_management.billing.repository.SubscriptionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Dunning — the retry ladder for {@code past_due} subscriptions (MD §6).
 *
 * <p>The fixed retry schedule (default {@code [1,3,7,14]} days) lives in config;
 * {@code past_due} carries {@code retry_count} / {@code next_retry_at} so this
 * job never re-derives what to do next. Each attempt is a fresh invoice (append-
 * only, auditable) and is idempotent, so redeliveries are harmless.
 */
@Slf4j
@Service
public class DunningService {

    private final SubscriptionRepository subscriptionRepository;
    private final BillingInvoiceRepository invoiceRepository;
    private final InvoiceService invoiceService;
    private final BillingPaymentService paymentService;
    private final SubscriptionService subscriptionService;

    private final List<Integer> retrySchedule;

    public DunningService(SubscriptionRepository subscriptionRepository,
                          BillingInvoiceRepository invoiceRepository,
                          InvoiceService invoiceService,
                          BillingPaymentService paymentService,
                          SubscriptionService subscriptionService,
                          @Value("${app.billing.dunning.schedule:1,3,7,14}") String schedule) {
        this.subscriptionRepository = subscriptionRepository;
        this.invoiceRepository = invoiceRepository;
        this.invoiceService = invoiceService;
        this.paymentService = paymentService;
        this.subscriptionService = subscriptionService;
        this.retrySchedule = parseSchedule(schedule);
    }

    @Transactional
    public void retry(UUID subscriptionId) {
        Subscription sub = subscriptionRepository.findByIdForUpdate(subscriptionId).orElse(null);
        if (sub == null || sub.getStatus() != SubscriptionStatus.PAST_DUE
                || sub.getNextRetryAt() == null
                || sub.getNextRetryAt().isAfter(LocalDateTime.now())) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        int attempt = sub.getRetryCount() + 1;
        String idem = "dunning:" + sub.getId() + ":" + sub.getCurrentPeriodEnd() + ":" + attempt;

        BillingInvoice invoice = invoiceRepository.findByIdempotencyKey(idem).orElseGet(() ->
                invoiceService.createInvoice(sub.getId(), idem, now, sub.getCurrentPeriodEnd(),
                        List.of(new InvoiceService.LineItem(
                                LineItemType.SUBSCRIPTION,
                                sub.getPlan().getName() + " subscription (dunning attempt " + attempt + ")",
                                sub.getPlanVersion().getPrice(),
                                sub.getCurrentPeriodStart(), sub.getCurrentPeriodEnd()))));

        switch (invoice.getStatus()) {
            case PAID -> recover(sub, now);
            case OPEN -> {
                BillingPaymentService.PaymentResult r = paymentService.attemptInvoicePayment(
                        invoice.getId(), null, "dunning-pay:" + idem);
                if (r.approved()) {
                    recover(sub, now);
                } else {
                    failAgain(sub, now);
                }
            }
            case FAILED -> failAgain(sub, now);
            default -> { /* draft — nothing to do */ }
        }
    }

    private void recover(Subscription sub, LocalDateTime now) {
        subscriptionService.transition(sub, SubscriptionStatus.ACTIVE, "dunning retry succeeded");
        sub.setRetryCount(0);
        sub.setNextRetryAt(null);
        sub.setCurrentPeriodStart(sub.getCurrentPeriodEnd());
        sub.setCurrentPeriodEnd(sub.getCurrentPeriodStart().plusMonths(1));
        subscriptionRepository.save(sub);
    }

    private void failAgain(Subscription sub, LocalDateTime now) {
        int newRetryCount = sub.getRetryCount() + 1;
        sub.setRetryCount(newRetryCount);
        if (newRetryCount > retrySchedule.size()) {
            subscriptionService.transition(sub, SubscriptionStatus.CANCELED,
                    "dunning exhausted after " + newRetryCount + " attempts");
        } else {
            sub.setNextRetryAt(now.plusDays(retrySchedule.get(newRetryCount - 1)));
            subscriptionRepository.save(sub);
            log.info("Subscription {} dunning attempt {} failed — next retry {}",
                    sub.getId(), newRetryCount, sub.getNextRetryAt());
        }
    }

    private List<Integer> parseSchedule(String schedule) {
        if (schedule == null || schedule.isBlank()) return List.of(1);
        return java.util.Arrays.stream(schedule.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Integer::valueOf)
                .toList();
    }
}
