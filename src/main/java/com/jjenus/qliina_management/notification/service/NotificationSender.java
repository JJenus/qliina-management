package com.jjenus.qliina_management.notification.service;

import com.jjenus.qliina_management.common.BusinessException;
import com.jjenus.qliina_management.identity.model.User;
import com.jjenus.qliina_management.identity.repository.UserRepository;
import com.jjenus.qliina_management.notification.dto.SendNotificationRequest;
import com.jjenus.qliina_management.notification.dto.TestNotificationRequest;
import com.jjenus.qliina_management.notification.model.*;
import com.jjenus.qliina_management.notification.repository.NotificationLogRepository;
import com.jjenus.qliina_management.notification.repository.NotificationRepository;
import com.jjenus.qliina_management.notification.repository.NotificationTemplateRepository;
import com.jjenus.qliina_management.notification.service.channel.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationSender {
    
    private final NotificationRepository notificationRepository;
    private final NotificationTemplateRepository templateRepository;
    private final NotificationLogRepository logRepository;
    private final UserRepository userRepository;
    
    private final EmailChannelService emailChannel;
    private final SmsChannelService smsChannel;
    private final PushChannelService pushChannel;
    private final WhatsAppChannelService whatsAppChannel;
    
    @Transactional
    public List<Notification> prepareNotifications(UUID businessId, SendNotificationRequest request) {
        Notification.NotificationType type = Notification.NotificationType.valueOf(request.getType());
        Notification.NotificationChannel channel = Notification.NotificationChannel.valueOf(request.getChannel());
        Notification.NotificationPriority priority = request.getPriority() != null ?
            Notification.NotificationPriority.valueOf(request.getPriority()) : Notification.NotificationPriority.NORMAL;
        
        List<Notification> notifications = new ArrayList<>();
        List<UUID> recipientIds = determineRecipients(businessId, request);
        
        for (UUID userId : recipientIds) {
            User user = userRepository.findById(userId).orElse(null);
            if (user == null) continue;
            
            Notification notification = createNotification(businessId, userId, type, channel, priority, request);
            notifications.add(notificationRepository.save(notification));
        }
        
        return notifications;
    }
    
    @Transactional
    public void deliver(UUID notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
            .orElseThrow(() -> new BusinessException("Notification not found", "NOTIFICATION_NOT_FOUND"));
        
        User user = userRepository.findById(notification.getUserId()).orElse(null);
        if (user == null) return;
        
        try {
            switch (notification.getChannel()) {
                case EMAIL:
                    emailChannel.send(notification, user);
                    break;
                case SMS:
                    smsChannel.send(notification, user);
                    break;
                case WHATSAPP:
                    whatsAppChannel.send(notification, user);
                    break;
                case PUSH:
                    pushChannel.send(notification, user);
                    break;
                case IN_APP:
                    notification.markAsDelivered();
                    break;
            }
            
            notification.markAsSent();
            notificationRepository.save(notification);
            
        } catch (Exception e) {
            notification.markAsFailed(e.getMessage());
            notification.setRetryCount(notification.getRetryCount() + 1);
            notificationRepository.save(notification);
            log.error("Failed to deliver notification: {}", notificationId, e);
        }
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
        }
    }
    
    private List<UUID> determineRecipients(UUID businessId, SendNotificationRequest request) {
        if (request.getRecipients() != null && !request.getRecipients().isEmpty()) {
            return request.getRecipients();
        }
        return userRepository.findUserIdsByBusinessId(businessId);
    }
    
    private Notification createNotification(UUID businessId, UUID userId, 
                                             Notification.NotificationType type,
                                             Notification.NotificationChannel channel,
                                             Notification.NotificationPriority priority,
                                             SendNotificationRequest request) {
        Notification notification = new Notification();
        notification.setBusinessId(businessId);
        notification.setUserId(userId);
        notification.setType(type);
        notification.setChannel(channel);
        notification.setPriority(priority);
        notification.setStatus(Notification.NotificationStatus.PENDING);
        notification.setScheduledFor(request.getScheduledFor());
        
        if (request.getTemplateId() != null) {
            NotificationTemplate template = templateRepository.findById(request.getTemplateId())
                .orElseThrow(() -> new BusinessException("Template not found", "TEMPLATE_NOT_FOUND"));
            
            Map<String, Object> data = new HashMap<>();
            if (request.getTemplateData() != null) {
                data.putAll(request.getTemplateData());
            }
            
            User user = userRepository.findById(userId).orElse(null);
            if (user != null) {
                data.put("userName", user.getFirstName() + " " + user.getLastName());
                data.put("userEmail", user.getEmail());
                data.put("userPhone", user.getPhone());
            }
            
            notification.setTitle(template.renderTitle(data));
            notification.setBody(template.renderBody(data));
            notification.setData(data);
            
        } else {
            notification.setTitle(request.getTitle());
            notification.setBody(request.getBody());
            notification.setData(request.getTemplateData());
        }
        
        return notification;
    }
}
