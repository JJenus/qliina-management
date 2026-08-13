package com.jjenus.qliina_management.notification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled jobs for delivery lifecycle (doc §4).
 *
 * processPendingDeliveries -- every 5s, dispatches deliveries not yet sent.
 * retryFailedDeliveries    -- every 30s, retries FAILED deliveries whose
 *                             backoff window elapsed and which still have
 *                             attempts left on their budget.
 *
 * Retry state lives on notification_deliveries, never on event history. Both
 * methods are @Async so they never block the scheduler thread pool. Registration
 * is conditional (app.notification.scheduler.enabled=false in tests).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.notification.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class NotificationScheduler {

    private final NotificationDeliveryService deliveryService;

    @Async
    @Scheduled(fixedDelay = 5000)
    public void processPendingDeliveries() {
        deliveryService.processDue();
    }

    @Async
    @Scheduled(fixedDelay = 30_000)
    public void retryFailedDeliveries() {
        deliveryService.processRetries();
    }
}