package com.jjenus.qliina_management.notification.service;

import com.jjenus.qliina_management.notification.model.NotificationOutbox;
import com.jjenus.qliina_management.notification.repository.NotificationOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Outbox poller (doc §5). Claims due rows with {@code FOR UPDATE SKIP LOCKED}
 * so multiple app instances split the work without double-processing, then
 * delegates the domain work to {@link NotificationOutboxProcessor}. A crashed
 * row keeps its PENDING status with a lease; once the lease expires it is
 * re-picked (queue-level redelivery). Message-level processing failures are
 * budgeted by {@link NotificationOutboxService#failOutbox} and eventually DEAD
 * (DLQ).
 *
 * <p>Scheduler registration is conditional so tests run deterministic —
 * {@code app.notification.scheduler.enabled=false} in the test profile.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.notification.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class NotificationOutboxWorker {

    private final NotificationOutboxRepository outboxRepository;
    private final NotificationOutboxProcessor processor;
    private final NotificationOutboxService outboxService;

    @Value("${app.notification.outbox.lease-timeout-ms:300000}")
    private long leaseTimeoutMs;

    @Async
    @Scheduled(fixedDelay = 2000)
    public void processDue() {
        LocalDateTime now = LocalDateTime.now();
        List<NotificationOutbox> due = outboxRepository.lockDue(now, now.minus(Duration.ofMillis(leaseTimeoutMs)));
        if (due.isEmpty()) return;
        for (NotificationOutbox row : due) {
            try {
                processor.process(row.getId());
            } catch (Exception e) {
                log.error("Outbox row {} failed to process: {}", row.getId(), e.getMessage(), e);
                outboxService.failOutbox(row.getId(), e.getMessage());
            }
        }
    }
}