package com.jjenus.qliina_management.notification.service;

import com.jjenus.qliina_management.common.websocket.WebSocketPublisher;
import com.jjenus.qliina_management.identity.model.User;
import com.jjenus.qliina_management.identity.repository.UserRepository;
import com.jjenus.qliina_management.notification.gateway.SimulatedChannelGateway;
import com.jjenus.qliina_management.notification.model.*;
import com.jjenus.qliina_management.notification.repository.NotificationDeliveryEventRepository;
import com.jjenus.qliina_management.notification.repository.NotificationDeliveryRepository;
import com.jjenus.qliina_management.notification.repository.NotificationRepository;
import com.jjenus.qliina_management.notification.service.channel.EmailChannelService;
import com.jjenus.qliina_management.notification.service.channel.PushChannelService;
import com.jjenus.qliina_management.notification.service.channel.SmsChannelService;
import com.jjenus.qliina_management.notification.service.channel.WhatsAppChannelService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Per-delivery dispatch, retry and event recording (doc §2, §4).
 *
 * <p>Retry state lives on {@link NotificationDelivery} — attempt_count,
 * max_attempts, next_retry_at, last_error_type — and the sweep queries are on
 * this table, never on event history. Failures branch on cause (doc §4):
 * PERMANENT failures (hard bounce, invalid push token) are exhausted
 * immediately; TRANSIENT failures get an exponential backoff and are retried by
 * the scheduler job until the attempt budget runs out.
 */
@Slf4j
@Service
public class NotificationDeliveryService {

    private final NotificationDeliveryRepository deliveryRepository;
    private final NotificationDeliveryEventRepository eventRepository;
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final EmailChannelService emailChannel;
    private final SmsChannelService smsChannel;
    private final PushChannelService pushChannel;
    private final WhatsAppChannelService whatsAppChannel;
    private final WebSocketPublisher webSocketPublisher;
    private final SimulatedChannelGateway simulatedGateway;

    private final int maxAttempts;

    public NotificationDeliveryService(NotificationDeliveryRepository deliveryRepository,
                                       NotificationDeliveryEventRepository eventRepository,
                                       NotificationRepository notificationRepository,
                                       UserRepository userRepository,
                                       EmailChannelService emailChannel,
                                       SmsChannelService smsChannel,
                                       PushChannelService pushChannel,
                                       WhatsAppChannelService whatsAppChannel,
                                       WebSocketPublisher webSocketPublisher,
                                       SimulatedChannelGateway simulatedGateway,
                                       @Value("${app.notification.delivery.max-attempts:5}") int maxAttempts) {
        this.deliveryRepository = deliveryRepository;
        this.eventRepository = eventRepository;
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.emailChannel = emailChannel;
        this.smsChannel = smsChannel;
        this.pushChannel = pushChannel;
        this.whatsAppChannel = whatsAppChannel;
        this.webSocketPublisher = webSocketPublisher;
        this.simulatedGateway = simulatedGateway;
        this.maxAttempts = maxAttempts;
    }

    // ---------------------------------------------------------------------
    // Creation
    // ---------------------------------------------------------------------

    @Transactional
    public NotificationDelivery createDelivery(UUID businessId, UUID notificationId,
                                               Notification.NotificationChannel channel,
                                               UUID deviceId, String recipient) {
        NotificationDelivery delivery = new NotificationDelivery();
        delivery.setBusinessId(businessId);
        delivery.setNotificationId(notificationId);
        delivery.setChannel(channel);
        delivery.setDeviceId(deviceId);
        delivery.setRecipient(recipient);
        delivery.setStatus(NotificationDeliveryStatus.PENDING);
        delivery.setAttemptCount(0);
        delivery.setMaxAttempts(maxAttempts);
        return deliveryRepository.save(delivery);
    }

    // ---------------------------------------------------------------------
    // Dispatch + retry (one attempt)
    // ---------------------------------------------------------------------

    /**
     * One dispatch attempt for a delivery row. Idempotent: already-sent or
     * exhausted deliveries are left untouched. Handles PENDING (first attempt)
     * and FAILED (retry attempt). Failures are recorded on the row and never
     * rethrown, so callers (outbox worker, sweep jobs, request path) flow on.
     */
    @Transactional
    public void dispatch(UUID deliveryId) {
        NotificationDelivery delivery = deliveryRepository.findByIdForUpdate(deliveryId).orElse(null);
        if (delivery == null) return;
        if (delivery.getStatus() == NotificationDeliveryStatus.SENT
                || delivery.getStatus() == NotificationDeliveryStatus.EXHAUSTED) {
            return;
        }
        delivery.setAttemptCount(delivery.getAttemptCount() + 1);
        try {
            Notification hub = requireHub(delivery);
            simulateProviderFailure();
            send(delivery, hub);
            markSent(delivery, hub);
        } catch (ChannelDeliveryException e) {
            recordFailure(delivery, e.isPermanent()
                    ? NotificationDeliveryErrorType.PERMANENT
                    : NotificationDeliveryErrorType.TRANSIENT, e.getMessage());
        } catch (Exception e) {
            log.warn("Delivery {} ({}) failed: {}", deliveryId, delivery.getChannel(), e.getMessage());
            recordFailure(delivery, NotificationDeliveryErrorType.TRANSIENT, e.getMessage());
        }
    }

    // ---------------------------------------------------------------------
    // Sweeps (called by the scheduler)
    // ---------------------------------------------------------------------

    @Transactional
    public void processDue() {
        List<NotificationDelivery> due = deliveryRepository.lockDueForDispatch(LocalDateTime.now());
        for (NotificationDelivery d : due) {
            dispatch(d.getId());
        }
    }

    @Transactional
    public void processRetries() {
        List<NotificationDelivery> due = deliveryRepository.lockDueForRetry(LocalDateTime.now());
        for (NotificationDelivery d : due) {
            dispatch(d.getId());
        }
    }

    // ---------------------------------------------------------------------
    // Interaction tracking (open / click)
    // ---------------------------------------------------------------------

    @Transactional
    public void open(UUID deliveryId) {
        NotificationDelivery delivery = deliveryRepository.findByIdForUpdate(deliveryId).orElse(null);
        if (delivery == null || delivery.getStatus() == NotificationDeliveryStatus.PENDING) return;
        recordEvent(delivery, NotificationDeliveryEventType.OPENED, "opened by recipient");
    }

    @Transactional
    public void click(UUID deliveryId) {
        NotificationDelivery delivery = deliveryRepository.findByIdForUpdate(deliveryId).orElse(null);
        if (delivery == null || delivery.getStatus() == NotificationDeliveryStatus.PENDING) return;
        recordEvent(delivery, NotificationDeliveryEventType.CLICKED, "link clicked");
    }

    /** Records an OPENED event on every sent delivery of a notification (in-app read model). */
    @Transactional
    public void recordOpenedForNotification(UUID notificationId) {
        for (NotificationDelivery d : deliveryRepository.findByNotificationIdOrderByCreatedAtAsc(notificationId)) {
            if (d.getStatus() == NotificationDeliveryStatus.SENT) {
                recordEvent(d, NotificationDeliveryEventType.OPENED, "marked read in-app");
            }
        }
    }

    // ---------------------------------------------------------------------
    // Internals
    // ---------------------------------------------------------------------

    private void send(NotificationDelivery delivery, Notification hub) {
        User user = userRepository.findById(hub.getUserId()).orElse(null);
        if (user == null) {
            throw new ChannelDeliveryException("Recipient user no longer exists",
                    "NOTIFICATION_USER_MISSING", true);
        }
        switch (delivery.getChannel()) {
            case EMAIL -> emailChannel.send(hub, user);
            case SMS -> smsChannel.send(hub, user);
            case WHATSAPP -> whatsAppChannel.send(hub, user);
            case PUSH -> pushChannel.send(hub, user);
            case IN_APP -> {
                hub.markAsDelivered();
                notificationRepository.save(hub);
                if (hub.getUserId() != null) {
                    webSocketPublisher.publishUserNotification(hub.getUserId(), hub);
                }
            }
        }
    }

    private void markSent(NotificationDelivery delivery, Notification hub) {
        delivery.setStatus(NotificationDeliveryStatus.SENT);
        delivery.setSentAt(LocalDateTime.now());
        delivery.setNextRetryAt(null);
        if (hub.getStatus() == Notification.NotificationStatus.PENDING) {
            hub.markAsSent();
            notificationRepository.save(hub);
        }
        deliveryRepository.save(delivery);
        recordEvent(delivery, NotificationDeliveryEventType.SENT, "accepted by channel");
        if (delivery.getChannel() == Notification.NotificationChannel.IN_APP) {
            recordEvent(delivery, NotificationDeliveryEventType.DELIVERED, "pushed over websocket");
        }
    }

    private void recordFailure(NotificationDelivery delivery,
                               NotificationDeliveryErrorType type, String error) {
        LocalDateTime now = LocalDateTime.now();
        delivery.setLastError(error);
        delivery.setLastErrorType(type);
        delivery.setStatus(NotificationDeliveryStatus.FAILED);
        delivery.setNextRetryAt(backoff(now, delivery.getAttemptCount()));
        if (type == NotificationDeliveryErrorType.PERMANENT
                || delivery.getAttemptCount() >= delivery.getMaxAttempts()) {
            delivery.setStatus(NotificationDeliveryStatus.EXHAUSTED);
            delivery.setNextRetryAt(null);
        }
        deliveryRepository.save(delivery);
        recordEvent(delivery, NotificationDeliveryEventType.FAILED, type + ": " + error);
    }

    private void recordEvent(NotificationDelivery delivery,
                             NotificationDeliveryEventType eventType, String detail) {
        NotificationDeliveryEvent event = new NotificationDeliveryEvent();
        event.setBusinessId(delivery.getBusinessId());
        event.setDeliveryId(delivery.getId());
        event.setEventType(eventType);
        event.setOccurredAt(LocalDateTime.now());
        event.setDetail(detail);
        eventRepository.save(event);
    }

    private Notification requireHub(NotificationDelivery delivery) {
        return notificationRepository.findById(delivery.getNotificationId())
                .orElseThrow(() -> new ChannelDeliveryException("Hub notification missing",
                        "NOTIFICATION_NOT_FOUND", true));
    }

    private void simulateProviderFailure() {
        if (simulatedGateway.isForceFailures()) {
            boolean permanent = simulatedGateway.isForcePermanentFailures();
            throw new ChannelDeliveryException(
                    "Simulated channel " + (permanent ? "permanent" : "transient") + " failure",
                    permanent ? "SIM_FAILURE_PERMANENT" : "SIM_FAILURE_TRANSIENT", permanent);
        }
    }

    /** Backoff from the doc (§4): exponential with jitter, capped at 5 minutes. */
    private LocalDateTime backoff(LocalDateTime now, int attemptCount) {
        long baseSeconds = Math.min(1L << attemptCount, 300L);
        long jitter = ThreadLocalRandom.current().nextLong(baseSeconds / 10L + 1L);
        return now.plusSeconds(baseSeconds + jitter);
    }
}