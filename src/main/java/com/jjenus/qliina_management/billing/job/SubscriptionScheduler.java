package com.jjenus.qliina_management.billing.job;

import com.jjenus.qliina_management.billing.model.Subscription;
import com.jjenus.qliina_management.billing.repository.SubscriptionRepository;
import com.jjenus.qliina_management.billing.service.BillingCycleService;
import com.jjenus.qliina_management.billing.service.DunningService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Recurring billing jobs. Discovery uses {@code FOR UPDATE SKIP LOCKED} so
 * multiple app instances divide the work; each item is re-locked and re-
 * validated inside its own transaction by the service, and every write is
 * idempotent — a crashed or overlapping sweep can never double-bill.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriptionScheduler {

    private final SubscriptionRepository subscriptionRepository;
    private final BillingCycleService billingCycleService;
    private final DunningService dunningService;

    @Scheduled(cron = "${app.billing.schedule.renewal:0 15 * * * *}")
    public void renewalSweep() {
        List<Subscription> due = subscriptionRepository.lockDueForRenewal(LocalDateTime.now());
        for (Subscription sub : due) {
            try {
                if (billingCycleService.renew(sub.getId())) {
                    log.info("Renewed subscription {} for business {}", sub.getId(), sub.getBusinessId());
                }
            } catch (Exception e) {
                log.error("Renewal failed for subscription {}: {}", sub.getId(), e.getMessage(), e);
            }
        }
    }

    @Scheduled(cron = "${app.billing.schedule.dunning:0 45 * * * *}")
    public void dunningSweep() {
        List<Subscription> due = subscriptionRepository.lockDueForRetry(LocalDateTime.now());
        for (Subscription sub : due) {
            try {
                dunningService.retry(sub.getId());
            } catch (Exception e) {
                log.error("Dunning retry failed for subscription {}: {}", sub.getId(), e.getMessage(), e);
            }
        }
    }

    @Scheduled(cron = "${app.billing.schedule.trial:0 30 * * * *}")
    public void trialExpirySweep() {
        List<Subscription> expired = subscriptionRepository.lockExpiredTrials(LocalDateTime.now());
        for (Subscription sub : expired) {
            try {
                billingCycleService.processExpiredTrial(sub.getId());
            } catch (Exception e) {
                log.error("Trial expiry failed for subscription {}: {}", sub.getId(), e.getMessage(), e);
            }
        }
    }

    @Scheduled(cron = "${app.billing.schedule.cancellation:0 5 * * * *}")
    public void cancellationSweep() {
        List<Subscription> lapsed = subscriptionRepository.lockPendingCancellations(LocalDateTime.now());
        for (Subscription sub : lapsed) {
            try {
                billingCycleService.finalizeCancellation(sub.getId());
            } catch (Exception e) {
                log.error("Cancellation finalize failed for subscription {}: {}", sub.getId(), e.getMessage(), e);
            }
        }
    }
}
