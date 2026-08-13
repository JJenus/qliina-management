package com.jjenus.qliina_management.notification.service;

import com.jjenus.qliina_management.common.BusinessException;
import com.jjenus.qliina_management.identity.model.User;
import com.jjenus.qliina_management.identity.repository.UserRepository;
import com.jjenus.qliina_management.notification.dto.SendNotificationRequest;
import com.jjenus.qliina_management.notification.dto.TestNotificationRequest;
import com.jjenus.qliina_management.notification.model.*;
import com.jjenus.qliina_management.notification.repository.NotificationRepository;
import com.jjenus.qliina_management.notification.repository.NotificationTemplateRepository;
import com.jjenus.qliina_management.notification.service.channel.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Send-path entry point (doc §5). Two routes:
 * <ul>
 *   <li><b>IN_APP</b> — synchronous: hub notification + delivery row + websocket
 *       push, preserving the in-app read model.</li>
 *   <li><b>External channels</b> — enqueues a transactional outbox row per
 *       recipient; the outbox worker creates the hub + deliveries and performs
 *       the first provider dispatch off the request thread.</li>
 * </ul>
 * Template lookup/rendering and recipient resolution stay synchronous so bad
 * requests (TEMPLATE_NOT_FOUND, invalid enum) fail fast at the API.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationSender {

    private final NotificationRepository notificationRepository;
    private final NotificationTemplateRepository templateRepository;
    private final UserRepository userRepository;
    private final NotificationDeliveryService deliveryService;
    private final NotificationOutboxService outboxService;

    private final EmailChannelService emailChannel;
    private final SmsChannelService smsChannel;
    private final WhatsAppChannelService whatsAppChannel;

    public record SendResult(Notification first, int queued) {
    }

    private record Rendered(UUID templateId, String title, String body,
                            Map<String, Object> data, boolean mandatory) {
    }

    @Transactional
    public SendResult send(UUID businessId, SendNotificationRequest request) {
        Notification.NotificationType type = Notification.NotificationType.valueOf(request.getType());
        Notification.NotificationChannel channel = Notification.NotificationChannel.valueOf(request.getChannel());
        Notification.NotificationPriority priority = request.getPriority() != null
                ? Notification.NotificationPriority.valueOf(request.getPriority())
                : Notification.NotificationPriority.NORMAL;

        List<UUID> recipients = determineRecipients(businessId, request);
        Notification first = null;
        int queued = 0;
        for (UUID userId : recipients) {
            User user = userRepository.findById(userId).orElse(null);
            if (user == null) continue;
            Rendered rendered = render(user, request);
            if (channel == Notification.NotificationChannel.IN_APP) {
                Notification hub = buildHub(businessId, userId, type, channel, priority,
                        rendered, request.getScheduledFor());
                hub = notificationRepository.save(hub);
                NotificationDelivery delivery = deliveryService.createDelivery(
                        businessId, hub.getId(), channel, null, null);
                deliveryService.dispatch(delivery.getId());
                if (first == null) first = hub;
            } else {
                outboxService.enqueue(businessId, userId, rendered.templateId(), type, channel,
                        rendered.mandatory(), priority, rendered.title(), rendered.body(),
                        rendered.data(), request.getScheduledFor());
                queued++;
            }
        }
        return new SendResult(first, queued);
    }

    /** Renders template-linked sends or falls back to ad-hoc content. */
    private Rendered render(User user, SendNotificationRequest request) {
        Map<String, Object> data = new HashMap<>();
        if (request.getTemplateData() != null) data.putAll(request.getTemplateData());
        data.put("userName", (user.getFirstName() != null ? user.getFirstName() : "") + " "
                + (user.getLastName() != null ? user.getLastName() : ""));
        data.put("userEmail", user.getEmail());
        data.put("userPhone", user.getPhone());

        if (request.getTemplateId() != null) {
            NotificationTemplate template = templateRepository.findById(request.getTemplateId())
                    .orElseThrow(() -> new BusinessException("Template not found", "TEMPLATE_NOT_FOUND"));
            return new Rendered(template.getId(), template.renderTitle(data),
                    template.renderBody(data), data, Boolean.TRUE.equals(template.getIsMandatory()));
        }
        return new Rendered(null, request.getTitle(), request.getBody(), data, false);
    }

    private Notification buildHub(UUID businessId, UUID userId,
                                  Notification.NotificationType type,
                                  Notification.NotificationChannel channel,
                                  Notification.NotificationPriority priority,
                                  Rendered rendered, LocalDateTime scheduledFor) {
        Notification notification = new Notification();
        notification.setBusinessId(businessId);
        notification.setUserId(userId);
        notification.setTemplateId(rendered.templateId());
        notification.setType(type);
        notification.setChannel(channel);
        notification.setPriority(priority);
        notification.setTitle(rendered.title());
        notification.setBody(rendered.body());
        notification.setData(rendered.data());
        notification.setStatus(Notification.NotificationStatus.PENDING);
        notification.setScheduledFor(scheduledFor);
        return notification;
    }

    private List<UUID> determineRecipients(UUID businessId, SendNotificationRequest request) {
        if (request.getRecipients() != null && !request.getRecipients().isEmpty()) {
            return request.getRecipients();
        }
        return userRepository.findUserIdsByBusinessId(businessId);
    }

    @Transactional
    public void sendTest(UUID businessId, TestNotificationRequest request) {
        if (request.getTemplateId() != null) {
            NotificationTemplate template = templateRepository.findById(request.getTemplateId())
                    .orElseThrow(() -> new BusinessException("Template not found", "TEMPLATE_NOT_FOUND"));

            String content = template.renderBody(request.getTemplateData());
            String subject = template.getSubject() != null ?
                    template.renderTitle(request.getTemplateData()) : null;

            sendTestByChannel(businessId, request.getChannel(), request.getRecipient(),
                    subject, content, request.getTemplateData());

        } else {
            sendTestByChannel(businessId, request.getChannel(), request.getRecipient(),
                    request.getCustomTitle(), request.getCustomBody(), null);
        }
    }

    private void sendTestByChannel(UUID businessId, String channel, String recipient,
                                   String subject, String content, Map<String, Object> data) {
        Notification.NotificationChannel ch = Notification.NotificationChannel.valueOf(channel);

        switch (ch) {
            case EMAIL:
                emailChannel.sendTest(businessId, recipient, subject, content);
                break;
            case SMS:
                smsChannel.sendTest(businessId, recipient, content);
                break;
            case WHATSAPP:
                whatsAppChannel.sendTest(businessId, recipient, content);
                break;
            case PUSH:
                log.warn("Push test notifications require a user ID");
                break;
            case IN_APP:
                log.warn("In-app notifications are created via /send");
                break;
        }
    }
}