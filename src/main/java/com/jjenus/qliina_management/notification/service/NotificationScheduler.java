package com.jjenus.qliina_management.notification.service;

import com.jjenus.qliina_management.notification.model.Notification;
import com.jjenus.qliina_management.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Scheduled jobs for notification lifecycle management.
 *
 * processScheduledNotifications -- every 30s, dispatches future-dated notifications.
 * retryFailedNotifications       -- every 5min, retries FAILED notifications
 *                                   up to MAX_RETRY times with a back-off window.
 *
 * Both methods are @Async so they never block the scheduler thread pool.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationScheduler {

    private static final int MAX_RETRY       = 3;
    private static final int RETRY_DELAY_MIN = 5;

    private final NotificationRepository notificationRepository;
    private final NotificationSender     notificationSender;

    @Async
    @Scheduled(fixedDelay = 30_000)
    public void processScheduledNotifications() {
        List<Notification> due = notificationRepository.findDueNotifications(LocalDateTime.now());
        if (due.isEmpty()) return;
        log.debug("Processing {} scheduled notifications", due.size());
        for (Notification n : due) {
            try {
                notificationSender.deliver(n.getId());
            } catch (Exception e) {
                log.error("Failed to deliver scheduled notification {}: {}", n.getId(), e.getMessage());
            }
        }
    }

    @Async
    @Scheduled(fixedDelay = 300_000)
    public void retryFailedNotifications() {
        LocalDateTime retryWindow = LocalDateTime.now().minusMinutes(RETRY_DELAY_MIN);
        List<Notification> retryable = notificationRepository.findRetryable(MAX_RETRY, retryWindow);
        if (retryable.isEmpty()) return;
        log.info("Retrying {} failed notifications", retryable.size());
        for (Notification n : retryable) {
            try {
                notificationSender.deliver(n.getId());
            } catch (Exception e) {
                log.error("Retry failed for notification {}: {}", n.getId(), e.getMessage());
            }
        }
    }
}
