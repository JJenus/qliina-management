package com.jjenus.qliina_management.notification.service;

import com.jjenus.qliina_management.identity.model.User;
import com.jjenus.qliina_management.identity.repository.UserRepository;
import com.jjenus.qliina_management.notification.model.*;
import com.jjenus.qliina_management.notification.repository.NotificationOutboxRepository;
import com.jjenus.qliina_management.notification.repository.NotificationRepository;
import com.jjenus.qliina_management.notification.repository.UserDeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Outbox consumer — the domain work behind the queue (doc §5): for each row
 * create the hub notification, fan out one delivery per enabled channel (doc §3
 * preference resolution), then make the first dispatch attempt for each. A row
 * whose processing throws is left PENDING with a lease and is redelivered (or
 * parked) by the sweeping poller.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationOutboxProcessor {

    private final NotificationOutboxRepository outboxRepository;
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final UserDeviceRepository userDeviceRepository;
    private final NotificationPreferenceService preferenceService;
    private final NotificationDeliveryService deliveryService;
    private final NotificationOutboxService outboxService;

    private record ResolvedChannel(Notification.NotificationChannel channel,
                                   UUID deviceId, String recipient) {
    }

    @Transactional
    public void process(UUID outboxId) {
        NotificationOutbox row = outboxRepository.findByIdForUpdate(outboxId).orElse(null);
        if (row == null || row.getStatus() != NotificationOutboxStatus.PENDING) return;

        // Claim under a lease so a crash mid-processing is reclaimable by another worker.
        LocalDateTime now = LocalDateTime.now();
        row.setLockedAt(now);
        row.setLockedBy(null);
        outboxRepository.save(row);

        if (row.getUserId() == null) {
            // Broadcast sends are not supported yet — park rather than loop.
            outboxService.markProcessed(outboxId);
            return;
        }
        User user = userRepository.findById(row.getUserId()).orElse(null);
        if (user == null) {
            outboxService.markProcessed(outboxId);
            return;
        }

        List<ResolvedChannel> channels = resolveChannels(row, user);
        if (channels.isEmpty()) {
            outboxService.markProcessed(outboxId);
            return;
        }

        Notification.NotificationChannel primary =
                row.getChannel() != null ? row.getChannel() : channels.get(0).channel();
        Notification hub = buildHub(row, primary);
        hub = notificationRepository.save(hub);

        for (ResolvedChannel rc : channels) {
            NotificationDelivery delivery = deliveryService.createDelivery(
                    row.getBusinessId(), hub.getId(), rc.channel(), rc.deviceId(), rc.recipient());
            deliveryService.dispatch(delivery.getId());
        }

        outboxService.markProcessed(outboxId);
    }

    /**
     * The single fan-out decision (doc §3): mandatory templates go to every
     * channel the user has a valid address/device for; normal templates go to
     * their declared channel only if the preference resolves to enabled. Push is
     * only offered when the user actually has an active device — never burn a
     * retry on a target that cannot exist.
     */
    private List<ResolvedChannel> resolveChannels(NotificationOutbox row, User user) {
        List<ResolvedChannel> out = new ArrayList<>();
        if (Boolean.TRUE.equals(row.getMandatory())) {
            if (isNotBlank(user.getEmail())) {
                out.add(new ResolvedChannel(Notification.NotificationChannel.EMAIL, null, user.getEmail()));
            }
            if (isNotBlank(user.getPhone())) {
                out.add(new ResolvedChannel(Notification.NotificationChannel.SMS, null, user.getPhone()));
                out.add(new ResolvedChannel(Notification.NotificationChannel.WHATSAPP, null, user.getPhone()));
            }
            if (hasActiveDevice(user.getId())) {
                out.add(new ResolvedChannel(Notification.NotificationChannel.PUSH, null, null));
            }
            out.add(new ResolvedChannel(Notification.NotificationChannel.IN_APP, null, null));
            return out;
        }

        Notification.NotificationChannel channel = row.getChannel();
        if (channel == null) return out;
        if (channel == Notification.NotificationChannel.PUSH && !hasActiveDevice(user.getId())) return out;
        boolean enabled = preferenceService.resolvePreference(
                row.getBusinessId(), user.getId(), row.getType(), channel);
        if (!enabled) return out;
        String recipient = switch (channel) {
            case EMAIL -> user.getEmail();
            case SMS, WHATSAPP -> user.getPhone();
            default -> null;
        };
        out.add(new ResolvedChannel(channel, null, recipient));
        return out;
    }

    private boolean hasActiveDevice(UUID userId) {
        return !userDeviceRepository.findByUserIdAndIsActiveTrue(userId).isEmpty();
    }

    private Notification buildHub(NotificationOutbox row, Notification.NotificationChannel channel) {
        Notification hub = new Notification();
        hub.setBusinessId(row.getBusinessId());
        hub.setUserId(row.getUserId());
        hub.setTemplateId(row.getTemplateId());
        hub.setType(row.getType());
        hub.setChannel(channel);
        hub.setPriority(row.getPriority() != null ? row.getPriority() : Notification.NotificationPriority.NORMAL);
        hub.setTitle(row.getTitle());
        hub.setBody(row.getBody());
        hub.setData(row.getData());
        hub.setStatus(Notification.NotificationStatus.PENDING);
        hub.setScheduledFor(row.getScheduledFor());
        return hub;
    }

    private boolean isNotBlank(String s) {
        return s != null && !s.isBlank();
    }
}