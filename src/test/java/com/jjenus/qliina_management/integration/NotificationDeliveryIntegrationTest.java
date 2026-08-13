package com.jjenus.qliina_management.integration;

import com.jjenus.qliina_management.notification.gateway.SimulatedChannelGateway;
import com.jjenus.qliina_management.notification.model.*;
import com.jjenus.qliina_management.notification.repository.*;
import com.jjenus.qliina_management.notification.service.NotificationDeliveryService;
import com.jjenus.qliina_management.notification.service.NotificationOutboxProcessor;
import com.jjenus.qliina_management.notification.service.NotificationOutboxService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the async delivery pipeline introduced in doc §4/§5:
 * the transactional outbox (enqueue -> claim -> process), per-channel delivery
 * rows, retry/backoff, EXHAUSTED + DEAD (DLQ), and preference fan-out.
 *
 * The background schedulers (NotificationScheduler, NotificationOutboxWorker)
 * are disabled in the test profile; tests here replicate the worker's claim loop
 * (lockDue + processor + failOutbox) deterministically and drive the delivery
 * sweep methods directly. Provider dispatch is made deterministic via
 * {@link SimulatedChannelGateway} flag injection.
 */
@lombok.extern.slf4j.Slf4j
class NotificationDeliveryIntegrationTest extends BaseIntegrationTest {

    @AfterEach
    void resetGateway() {
        simulatedGateway.setForceFailures(false);
        simulatedGateway.setForcePermanentFailures(false);
    }

    @Autowired
    private NotificationOutboxRepository outboxRepository;
    @Autowired
    private NotificationOutboxProcessor outboxProcessor;
    @Autowired
    private NotificationOutboxService outboxService;
    @Autowired
    private NotificationDeliveryRepository deliveryRepository;
    @Autowired
    private NotificationDeliveryEventRepository eventRepository;
    @Autowired
    private NotificationDeliveryService deliveryService;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private SimulatedChannelGateway simulatedGateway;

    // ---------------------------------------------------------------------
    // Helpers (mirror the outbox worker / scheduler loop deterministically)
    // ---------------------------------------------------------------------

    private String base(UUID businessId) {
        return "/api/v1/" + businessId + "/notifications";
    }

    private void setupSms(AuthContext ctx) throws Exception {
        post("/api/v1/" + ctx.businessId() + "/notifications/config/sms", ctx.accessToken(), java.util.Map.of(
                "provider", "TWILIO",
                "fromNumber", "+15551234567",
                "accountSid", "sid",
                "authToken", "token"))
                .andExpect(status().isOk());
    }

    private void sendSms(AuthContext ctx) throws Exception {
        post(base(ctx.businessId()) + "/send", ctx.accessToken(), java.util.Map.of(
                "recipients", List.of(ctx.userId().toString()),
                "type", "ALERT",
                "channel", "SMS",
                "title", "Low stock",
                "body", "Item X is below threshold",
                "priority", "HIGH"))
                .andExpect(status().isOk());
    }

    /** Replicates NotificationOutboxWorker.processDue() exactly. */
    private void drainOutbox() {
        LocalDateTime now = LocalDateTime.now();
        List<NotificationOutbox> due = outboxRepository.lockDue(now, now.minus(Duration.ofMinutes(5)));
        for (NotificationOutbox row : due) {
            try {
                outboxProcessor.process(row.getId());
            } catch (Exception e) {
                outboxService.failOutbox(row.getId(), e.getMessage());
            }
        }
    }

    private NotificationOutbox singlePendingOutbox(AuthContext ctx) {
        return outboxRepository.findAll().stream()
                .filter(o -> o.getBusinessId().equals(ctx.businessId()))
                .findFirst().orElseThrow();
    }

    private UUID hubNotificationId(AuthContext ctx) throws Exception {
        var rs = get(base(ctx.businessId()), ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andReturn();
        return readUuid(rs.getResponse().getContentAsString(), "$.content[0].id");
    }

    private NotificationDelivery singleDelivery(UUID hubId, Notification.NotificationChannel channel) {
        return deliveryRepository.findByNotificationIdOrderByCreatedAtAsc(hubId).stream()
                .filter(d -> d.getChannel() == channel)
                .findFirst().orElseThrow();
    }

    // ---------------------------------------------------------------------
    // Outbox -> hub + delivery creation
    // ---------------------------------------------------------------------

    @Test
    void externalSend_enqueuesOutbox_thenClaimCreatesHubAndSends() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        setupSms(ctx);

        sendSms(ctx);

        NotificationOutbox queued = singlePendingOutbox(ctx);
        assertEquals(NotificationOutboxStatus.PENDING, queued.getStatus());
        assertEquals(0, queued.getAttemptCount());
        assertEquals(Notification.NotificationChannel.SMS, queued.getChannel());

        drainOutbox();

        NotificationOutbox processed = outboxRepository.findById(queued.getId()).orElseThrow();
        assertEquals(NotificationOutboxStatus.PROCESSED, processed.getStatus());
        assertNotNull(processed.getProcessedAt());

        UUID hubId = hubNotificationId(ctx);
        NotificationDelivery delivery = singleDelivery(hubId, Notification.NotificationChannel.SMS);
        assertEquals(NotificationDeliveryStatus.SENT, delivery.getStatus());
        assertEquals(1, delivery.getAttemptCount());
        assertEquals(ctx.phone(), delivery.getRecipient());
        assertNotNull(delivery.getSentAt());

        List<NotificationDeliveryEvent> events = eventRepository.findAllByDeliveryIdOrderByOccurredAtAsc(delivery.getId());
        assertTrue(events.stream().anyMatch(e -> e.getEventType() == NotificationDeliveryEventType.SENT));
        assertTrue(events.stream().noneMatch(e -> e.getEventType() == NotificationDeliveryEventType.FAILED));

        get(base(ctx.businessId()), ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("SENT"));
    }

    @Test
    void outboxRow_processingFailure_isDeadLettered() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();

        // Null title violates notifications.title NOT NULL -> processor save must throw.
        NotificationOutbox row = outboxService.enqueue(ctx.businessId(), ctx.userId(), null,
                Notification.NotificationType.ALERT, Notification.NotificationChannel.EMAIL,
                false, Notification.NotificationPriority.NORMAL, null, "body", null, null);
        assertEquals(NotificationOutboxStatus.PENDING, row.getStatus());

        drainOutbox();

        NotificationOutbox failed = outboxRepository.findById(row.getId()).orElseThrow();
        assertEquals(NotificationOutboxStatus.PENDING, failed.getStatus());
        assertEquals(1, failed.getAttemptCount());
        assertNotNull(failed.getNextAttemptAt());
        assertNotNull(failed.getLastError());

        // Force the row past its budget -> DEAD (DLQ).
        failed.setMaxAttempts(2);
        outboxRepository.save(failed);
        outboxService.failOutbox(failed.getId(), "forced");

        NotificationOutbox dead = outboxRepository.findById(row.getId()).orElseThrow();
        assertEquals(NotificationOutboxStatus.DEAD, dead.getStatus());
        assertEquals(2, dead.getAttemptCount());
        assertNull(dead.getNextAttemptAt());
        assertNull(dead.getLockedAt());
    }

    // ---------------------------------------------------------------------
    // Delivery retries + exhaustion
    // ---------------------------------------------------------------------

    @Test
    void transientFailure_retriesAfterBackoff_thenSucceeds() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        setupSms(ctx);

        simulatedGateway.setForceFailures(true);
        sendSms(ctx);
        drainOutbox();
        simulatedGateway.setForceFailures(false);

        UUID hubId = hubNotificationId(ctx);
        NotificationDelivery failed = singleDelivery(hubId, Notification.NotificationChannel.SMS);
        assertEquals(NotificationDeliveryStatus.FAILED, failed.getStatus());
        assertEquals(NotificationDeliveryErrorType.TRANSIENT, failed.getLastErrorType());
        assertEquals(1, failed.getAttemptCount());
        assertNotNull(failed.getNextRetryAt());
        assertTrue(failed.getNextRetryAt().isAfter(LocalDateTime.now()));

        failed.setNextRetryAt(LocalDateTime.now().minusSeconds(1));
        deliveryRepository.save(failed);
        deliveryService.processRetries();

        NotificationDelivery sent = deliveryRepository.findById(failed.getId()).orElseThrow();
        assertEquals(NotificationDeliveryStatus.SENT, sent.getStatus());
        assertEquals(2, sent.getAttemptCount());

        List<NotificationDeliveryEvent> events = eventRepository.findAllByDeliveryIdOrderByOccurredAtAsc(failed.getId());
        assertEquals(NotificationDeliveryEventType.FAILED, events.get(0).getEventType());
        assertEquals(NotificationDeliveryEventType.SENT, events.get(events.size() - 1).getEventType());
        assertEquals(0, deliveryRepository.findByNotificationIdOrderByCreatedAtAsc(hubId)
                .stream().filter(d -> d.getStatus() != NotificationDeliveryStatus.SENT).count());
    }

    @Test
    void permanentFailure_isImmediatelyExhausted() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        setupSms(ctx);

        simulatedGateway.setForceFailures(true);
        simulatedGateway.setForcePermanentFailures(true);
        sendSms(ctx);
        drainOutbox();

        UUID hubId = hubNotificationId(ctx);
        NotificationDelivery exhausted = singleDelivery(hubId, Notification.NotificationChannel.SMS);
        assertEquals(NotificationDeliveryStatus.EXHAUSTED, exhausted.getStatus());
        assertEquals(NotificationDeliveryErrorType.PERMANENT, exhausted.getLastErrorType());
        assertEquals(1, exhausted.getAttemptCount());
        assertNull(exhausted.getNextRetryAt());
    }

    @Test
    void transientRetries_exhaustAfterBudgetting() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        setupSms(ctx);

        simulatedGateway.setForceFailures(true);
        sendSms(ctx);
        drainOutbox();

        UUID hubId = hubNotificationId(ctx);
        NotificationDelivery first = singleDelivery(hubId, Notification.NotificationChannel.SMS);
        assertEquals(NotificationDeliveryStatus.FAILED, first.getStatus());

        first.setMaxAttempts(2);
        first.setNextRetryAt(LocalDateTime.now().minusSeconds(1));
        deliveryRepository.save(first);

        deliveryService.processRetries();

        NotificationDelivery exhausted = deliveryRepository.findById(first.getId()).orElseThrow();
        assertEquals(NotificationDeliveryStatus.EXHAUSTED, exhausted.getStatus());
        assertEquals(2, exhausted.getAttemptCount());
        assertNull(exhausted.getNextRetryAt());
    }

    // ---------------------------------------------------------------------
    // Preference fan-out
    // ---------------------------------------------------------------------

    @Test
    void preferenceDisabled_skipsChannelNoDeliveries() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();

        put(base(ctx.businessId()) + "/preferences", ctx.accessToken(), java.util.Map.of(
                "channel", "EMAIL",
                "notificationType", "ALERT",
                "enabled", false))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        post(base(ctx.businessId()) + "/send", ctx.accessToken(), java.util.Map.of(
                "recipients", List.of(ctx.userId().toString()),
                "type", "ALERT",
                "channel", "EMAIL",
                "title", "t",
                "body", "b"))
                .andExpect(status().isOk());

        NotificationOutbox queued = singlePendingOutbox(ctx);
        assertEquals(Notification.NotificationChannel.EMAIL, queued.getChannel());

        drainOutbox();

        NotificationOutbox processed = outboxRepository.findById(queued.getId()).orElseThrow();
        assertEquals(NotificationOutboxStatus.PROCESSED, processed.getStatus());
        assertEquals(0, notificationRepository.findAll().stream()
                .filter(n -> n.getBusinessId().equals(ctx.businessId())).count());
    }

    @Test
    void mandatoryEnqueue_bypassesPreferencesFansOutToAllAddresses() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();

        // Turn every preference off, then enqueue a MANDATORY row (bypasses the check).
        for (Notification.NotificationChannel ch : new Notification.NotificationChannel[]{
                Notification.NotificationChannel.EMAIL,
                Notification.NotificationChannel.SMS,
                Notification.NotificationChannel.WHATSAPP,
                Notification.NotificationChannel.PUSH}) {
            put(base(ctx.businessId()) + "/preferences", ctx.accessToken(), java.util.Map.of(
                    "channel", ch.name(),
                    "notificationType", "ALERT",
                    "enabled", false))
                    .andExpect(status().isOk());
        }

        outboxService.enqueue(ctx.businessId(), ctx.userId(), null,
                Notification.NotificationType.ALERT, null, true,
                Notification.NotificationPriority.NORMAL, "Mandatory title", "Mandatory body", null, null);

        drainOutbox();

        UUID hubId = hubNotificationId(ctx);
        List<NotificationDelivery> deliveries = deliveryRepository.findByNotificationIdOrderByCreatedAtAsc(hubId);
        // Owner has email + phone, no registered device -> EMAIL, SMS, WHATSAPP, IN_APP.
        assertEquals(4, deliveries.size());
        Notification hub = notificationRepository.findById(hubId).orElseThrow();
        assertEquals(Notification.NotificationChannel.EMAIL, hub.getChannel());
        // Dispatch of still-unconfigured channels returns FAILED rows, never throws.
        assertTrue(deliveries.stream().allMatch(d -> d.getStatus() == NotificationDeliveryStatus.SENT
                || d.getStatus() == NotificationDeliveryStatus.FAILED));
    }

    @Test
    void pushWithoutRegisteredDevice_isProcessed_withoutDeliveries() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();

        post(base(ctx.businessId()) + "/send", ctx.accessToken(), java.util.Map.of(
                "recipients", List.of(ctx.userId().toString()),
                "type", "ALERT",
                "channel", "PUSH",
                "title", "t",
                "body", "b"))
                .andExpect(status().isOk());

        NotificationOutbox queued = singlePendingOutbox(ctx);
        drainOutbox();

        NotificationOutbox processed = outboxRepository.findById(queued.getId()).orElseThrow();
        assertEquals(NotificationOutboxStatus.PROCESSED, processed.getStatus());
        assertEquals(0, notificationRepository.findAll().stream()
                .filter(n -> n.getBusinessId().equals(ctx.businessId())).count());
    }

    // ---------------------------------------------------------------------
    // Scheduled sends
    // ---------------------------------------------------------------------

    @Test
    void scheduledSend_notDueUntilWindow_thenProcessedWhenDue() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        setupSms(ctx);

        post(base(ctx.businessId()) + "/send", ctx.accessToken(), java.util.Map.of(
                "recipients", List.of(ctx.userId().toString()),
                "type", "ALERT",
                "channel", "SMS",
                "title", "t",
                "body", "b",
                "scheduledFor", LocalDateTime.now().plusHours(1).toString()))
                .andExpect(status().isOk());

        NotificationOutbox queued = singlePendingOutbox(ctx);
        assertNotNull(queued.getScheduledFor());
        assertTrue(queued.getScheduledFor().isAfter(LocalDateTime.now()));

        drainOutbox();

        NotificationOutbox stillPending = outboxRepository.findById(queued.getId()).orElseThrow();
        assertEquals(NotificationOutboxStatus.PENDING, stillPending.getStatus());
        assertEquals(0, notificationRepository.findAll().stream()
                .filter(n -> n.getBusinessId().equals(ctx.businessId())).count());

        stillPending.setScheduledFor(LocalDateTime.now().minusMinutes(1));
        outboxRepository.save(stillPending);
        drainOutbox();

        NotificationOutbox processed = outboxRepository.findById(queued.getId()).orElseThrow();
        assertEquals(NotificationOutboxStatus.PROCESSED, processed.getStatus());
        UUID hubId = hubNotificationId(ctx);
        assertEquals(NotificationDeliveryStatus.SENT,
                singleDelivery(hubId, Notification.NotificationChannel.SMS).getStatus());
    }
}