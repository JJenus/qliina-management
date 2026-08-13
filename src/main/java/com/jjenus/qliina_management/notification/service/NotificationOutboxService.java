package com.jjenus.qliina_management.notification.service;

import com.jjenus.qliina_management.notification.model.*;
import com.jjenus.qliina_management.notification.repository.NotificationOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Producer side of the transactional outbox (doc §5). {@link #enqueue} is the
 * only way notification triggers enter the queue; the worker/processor consume
 * rows. {@link #failOutbox} implements the message-level retry budget and the
 * DEAD-letter state (DLQ) for rows whose processing kept failing.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationOutboxService {

    private final NotificationOutboxRepository outboxRepository;

    @Transactional
    public NotificationOutbox enqueue(UUID businessId, UUID userId, UUID templateId,
                                      Notification.NotificationType type,
                                      Notification.NotificationChannel channel,
                                      boolean mandatory,
                                      Notification.NotificationPriority priority,
                                      String title, String body, Map<String, Object> data,
                                      LocalDateTime scheduledFor) {
        NotificationOutbox row = new NotificationOutbox();
        row.setBusinessId(businessId);
        row.setUserId(userId);
        row.setTemplateId(templateId);
        row.setType(type);
        row.setChannel(channel);
        row.setMandatory(mandatory);
        row.setPriority(priority);
        row.setTitle(title);
        row.setBody(body);
        row.setData(data);
        row.setScheduledFor(scheduledFor);
        row.setStatus(NotificationOutboxStatus.PENDING);
        row.setAttemptCount(0);
        return outboxRepository.save(row);
    }

    @Transactional
    public void markProcessed(UUID outboxId) {
        NotificationOutbox row = outboxRepository.findById(outboxId).orElse(null);
        if (row == null || row.getStatus() != NotificationOutboxStatus.PENDING) return;
        row.setStatus(NotificationOutboxStatus.PROCESSED);
        row.setProcessedAt(LocalDateTime.now());
        row.setLockedAt(null);
        row.setLockedBy(null);
        outboxRepository.save(row);
    }

    /** Message-level failure: counts against the row's own budget, then DEAD (DLQ). */
    @Transactional
    public void failOutbox(UUID outboxId, String error) {
        NotificationOutbox row = outboxRepository.findByIdForUpdate(outboxId).orElse(null);
        if (row == null || row.getStatus() != NotificationOutboxStatus.PENDING) return;
        LocalDateTime now = LocalDateTime.now();
        row.setAttemptCount(row.getAttemptCount() + 1);
        row.setLastError(error);
        row.setNextAttemptAt(backoff(now, row.getAttemptCount()));
        if (row.getAttemptCount() >= row.getMaxAttempts()) {
            row.setStatus(NotificationOutboxStatus.DEAD);
            row.setNextAttemptAt(null);
        }
        row.setLockedAt(null);
        row.setLockedBy(null);
        outboxRepository.save(row);
    }

    private LocalDateTime backoff(LocalDateTime now, int attemptCount) {
        long baseSeconds = Math.min(1L << attemptCount, 300L);
        long jitter = ThreadLocalRandom.current().nextLong(baseSeconds / 10L + 1L);
        return now.plusSeconds(baseSeconds + jitter);
    }
}