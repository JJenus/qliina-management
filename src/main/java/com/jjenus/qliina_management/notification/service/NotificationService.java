package com.jjenus.qliina_management.notification.service;

import com.jjenus.qliina_management.common.BusinessException;
import com.jjenus.qliina_management.common.PageResponse;
import com.jjenus.qliina_management.identity.model.User;
import com.jjenus.qliina_management.identity.repository.UserRepository;
import com.jjenus.qliina_management.notification.dto.*;
import com.jjenus.qliina_management.notification.model.*;
import com.jjenus.qliina_management.notification.repository.*;
import com.jjenus.qliina_management.order.model.Order;
import com.jjenus.qliina_management.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jjenus.qliina_management.notification.model.NotificationLog.DeliveryStatus;
import java.util.stream.Collectors;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {
    
    private final NotificationRepository notificationRepository;
    private final NotificationTemplateRepository templateRepository;
    private final NotificationLogRepository logRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final EmailService emailService;
    private final SMSService smsService;
    private final PushNotificationService pushService;
    private final WhatsAppService whatsAppService;
    
    // ==================== Notification Management ====================
    
    @Transactional(readOnly = true)
    public PageResponse<NotificationDTO> getNotifications(UUID businessId, UUID userId, 
                                                            String type, String status,
                                                            LocalDateTime fromDate, LocalDateTime toDate,
                                                            Pageable pageable) {
        Notification.NotificationType notificationType = type != null ? 
            Notification.NotificationType.valueOf(type) : null;
        Notification.NotificationStatus notificationStatus = status != null ?
            Notification.NotificationStatus.valueOf(status) : null;
        
        Page<Notification> page = notificationRepository.findByFilters(
            businessId, userId, notificationType, notificationStatus, fromDate, toDate, pageable);
        
        return PageResponse.from(page.map(this::mapToDTO));
    }
    
    @Transactional(readOnly = true)
    public Long getUnreadCount(UUID userId) {
        return notificationRepository.countUnreadByUserId(userId);
    }
    
    @Transactional
    public void markAsRead(MarkReadRequest request, UUID userId) {
        if (request.getMarkAll() != null && request.getMarkAll()) {
            notificationRepository.markAllAsRead(userId, LocalDateTime.now());
        } else if (request.getNotificationIds() != null) {
            for (UUID id : request.getNotificationIds()) {
                notificationRepository.markAsRead(id, LocalDateTime.now());
            }
        }
    }
    
    @Transactional
    public NotificationDTO sendNotification(UUID businessId, SendNotificationRequest request) {
        Notification.NotificationType type = Notification.NotificationType.valueOf(request.getType());
        Notification.NotificationChannel channel = Notification.NotificationChannel.valueOf(request.getChannel());
        Notification.NotificationPriority priority = request.getPriority() != null ?
            Notification.NotificationPriority.valueOf(request.getPriority()) : Notification.NotificationPriority.NORMAL;
        
        List<Notification> notifications = new ArrayList<>();
        
        // Determine recipients
        List<UUID> recipientIds = request.getRecipients();
        if (recipientIds == null || recipientIds.isEmpty()) {
            // Broadcast to all users in business
            recipientIds = userRepository.findUserIdsByBusinessId(businessId);
        }
        
        for (UUID userId : recipientIds) {
            User user = userRepository.findById(userId).orElse(null);
            if (user == null) continue;
            
            Notification notification = new Notification();
            notification.setBusinessId(businessId);
            notification.setUserId(userId);
            notification.setType(type);
            notification.setChannel(channel);
            notification.setPriority(priority);
            notification.setStatus(Notification.NotificationStatus.PENDING);
            notification.setScheduledFor(request.getScheduledFor());
            
            // Use template if provided
            if (request.getTemplateId() != null) {
                NotificationTemplate template = templateRepository.findById(request.getTemplateId())
                    .orElseThrow(() -> new BusinessException("Template not found", "TEMPLATE_NOT_FOUND"));
                
                Map<String, Object> data = new HashMap<>();
                if (request.getTemplateData() != null) {
                    data.putAll(request.getTemplateData());
                }
                // Add user data
                data.put("userName", user.getFirstName() + " " + user.getLastName());
                data.put("userEmail", user.getEmail());
                data.put("userPhone", user.getPhone());
                
                notification.setTitle(template.renderTitle(data));
                notification.setBody(template.renderBody(data));
                notification.setData(data);
                
            } else {
                notification.setTitle(request.getTitle());
                notification.setBody(request.getBody());
                notification.setData(request.getTemplateData());
            }
            
            notifications.add(notificationRepository.save(notification));
        }
        
        // Send immediately if not scheduled
        if (request.getScheduledFor() == null || request.getScheduledFor().isBefore(LocalDateTime.now())) {
            for (Notification notification : notifications) {
                try {
                    sendNotification(notification.getId());
                } catch (Exception e) {
                    log.error("Failed to send notification: {}", notification.getId(), e);
                }
            }
        }
        
        return notifications.isEmpty() ? null : mapToDTO(notifications.get(0));
    }
    
    @Transactional
    public void sendNotification(UUID notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
            .orElseThrow(() -> new BusinessException("Notification not found", "NOTIFICATION_NOT_FOUND"));
        
        User user = userRepository.findById(notification.getUserId()).orElse(null);
        if (user == null) return;
        
        try {
            switch (notification.getChannel()) {
                case EMAIL:
                    sendEmailNotification(notification, user);
                    break;
                case SMS:
                    sendSMSNotification(notification, user);
                    break;
                case WHATSAPP:
                    sendWhatsAppNotification(notification, user);
                    break;
                case PUSH:
                    sendPushNotification(notification, user);
                    break;
                case IN_APP:
                    // Already saved in database, just mark as delivered
                    notification.markAsDelivered();
                    break;
            }
            
            notification.markAsSent();
            notificationRepository.save(notification);
            
        } catch (Exception e) {
            notification.markAsFailed(e.getMessage());
            notification.setRetryCount(notification.getRetryCount() + 1);
            notificationRepository.save(notification);
            throw e;
        }
    }
    
    private void sendEmailNotification(Notification notification, User user) {
        emailService.sendEmail(
            notification.getBusinessId(),
            user.getEmail(),
            notification.getTitle(),
            notification.getBody()
        );
        
        createLog(notification, user.getEmail(), "Email sent successfully");
    }
    
    private void sendSMSNotification(Notification notification, User user) {
        smsService.sendSMS(
            notification.getBusinessId(),
            user.getPhone(),
            notification.getBody()
        );
        
        createLog(notification, user.getPhone(), "SMS sent successfully");
    }
    
    private void sendWhatsAppNotification(Notification notification, User user) {
        whatsAppService.sendWhatsAppMessage(
            notification.getBusinessId(),
            user.getPhone(),
            notification.getBody()
        );
        
        createLog(notification, user.getPhone(), "WhatsApp message sent successfully");
    }
    
    private void sendPushNotification(Notification notification, User user) {
        pushService.sendPushNotification(
            notification.getBusinessId(),
            user.getId(),
            notification.getTitle(),
            notification.getBody(),
            notification.getData()
        );
        
        createLog(notification, user.getId().toString(), "Push notification sent");
    }
    
    private void createLog(Notification notification, String recipient, String message) {
        NotificationLog log = new NotificationLog();
        log.setBusinessId(notification.getBusinessId());
        log.setNotification(notification);
        log.setRecipient(recipient);
        log.setChannel(notification.getChannel());
        log.setStatus(NotificationLog.DeliveryStatus.SENT);
        log.setSubject(notification.getTitle());
        log.setContent(notification.getBody());
        log.setSentAt(LocalDateTime.now());
        log.setProviderResponse(message);
        
        logRepository.save(log);
    }
    
    // ==================== Template Management ====================
    
    @Transactional(readOnly = true)
    public List<NotificationTemplateDTO> getTemplates(UUID businessId) {
        return templateRepository.findByBusinessId(businessId).stream()
            .map(this::mapToTemplateDTO)
            .collect(Collectors.toList());
    }
    
    @Transactional
    public NotificationTemplateDTO createTemplate(UUID businessId, CreateTemplateRequest request) {
        NotificationTemplate template = new NotificationTemplate();
        template.setBusinessId(businessId);
        template.setName(request.getName());
        template.setDescription(request.getDescription());
        template.setType(Notification.NotificationType.valueOf(request.getType()));
        template.setChannel(Notification.NotificationChannel.valueOf(request.getChannel()));
        template.setSubject(request.getSubject());
        template.setTitleTemplate(request.getTitleTemplate());
        template.setBodyTemplate(request.getBodyTemplate());
        template.setVariables(request.getVariables());
        template.setIsActive(true);
        
        template = templateRepository.save(template);
        return mapToTemplateDTO(template);
    }
    
    @Transactional
    public NotificationTemplateDTO updateTemplate(UUID templateId, UpdateTemplateRequest request) {
        NotificationTemplate template = templateRepository.findById(templateId)
            .orElseThrow(() -> new BusinessException("Template not found", "TEMPLATE_NOT_FOUND"));
        
        if (request.getName() != null) template.setName(request.getName());
        if (request.getDescription() != null) template.setDescription(request.getDescription());
        if (request.getSubject() != null) template.setSubject(request.getSubject());
        if (request.getTitleTemplate() != null) template.setTitleTemplate(request.getTitleTemplate());
        if (request.getBodyTemplate() != null) template.setBodyTemplate(request.getBodyTemplate());
        if (request.getIsActive() != null) template.setIsActive(request.getIsActive());
        
        template = templateRepository.save(template);
        return mapToTemplateDTO(template);
    }
    
    // ==================== Test & Logs ====================
    
    @Transactional
    public void testNotification(UUID businessId, TestNotificationRequest request) {
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
                emailService.sendEmail(businessId, recipient, subject, content);
                break;
            case SMS:
                smsService.sendSMS(businessId, recipient, content);
                break;
            case WHATSAPP:
                whatsAppService.sendWhatsAppMessage(businessId, recipient, content);
                break;
            case PUSH:
                // Would need user ID for push
                break;
        }
    }
    
    @Transactional(readOnly = true)
    public PageResponse<NotificationLogDTO> getNotificationLogs(UUID businessId, String channel,
                                                                  String status, LocalDateTime fromDate,
                                                                  LocalDateTime toDate, Pageable pageable) {
        Notification.NotificationChannel ch = channel != null ?
            Notification.NotificationChannel.valueOf(channel) : null;
        NotificationLog.DeliveryStatus deliveryStatus = status != null ?
            NotificationLog.DeliveryStatus.valueOf(status) : null;
        
        Page<NotificationLog> page = logRepository.findByFilters(
            businessId, ch, deliveryStatus, fromDate, toDate, pageable);
        
        return PageResponse.from(page.map(this::mapToLogDTO));
    }
    
    // ==================== Business Logic Notifications ====================
    
    @Transactional
    public void sendOrderStatusNotification(UUID businessId, UUID orderId, String oldStatus, String newStatus) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new BusinessException("Order not found", "ORDER_NOT_FOUND"));
        
        User customer = userRepository.findById(order.getCustomerId())
            .orElseThrow(() -> new BusinessException("Customer not found", "CUSTOMER_NOT_FOUND"));
        
        Map<String, Object> data = new HashMap<>();
        data.put("orderId", order.getId().toString());
        data.put("orderNumber", order.getOrderNumber());
        data.put("oldStatus", oldStatus);
        data.put("newStatus", newStatus);
        data.put("customerName", customer.getFirstName());
        
        // Send via multiple channels based on customer preferences
        SendNotificationRequest request = new SendNotificationRequest();
        request.setRecipients(Collections.singletonList(customer.getId()));
        request.setType("ORDER_STATUS");
        request.setTemplateData(data);
        
        // Find appropriate template
        Optional<NotificationTemplate> template = templateRepository.findActiveTemplate(
            businessId, Notification.NotificationType.ORDER_STATUS, Notification.NotificationChannel.SMS);
        
        template.ifPresent(t -> {
            request.setTemplateId(t.getId());
            request.setChannel("SMS");
            sendNotification(businessId, request);
        });
        
        // Also send in-app notification
        request.setChannel("IN_APP");
        request.setTemplateId(null);
        request.setTitle("Order Status Update");
        request.setBody(String.format("Your order %s is now %s", order.getOrderNumber(), newStatus));
        sendNotification(businessId, request);
    }
    
    @Transactional
    public void sendPaymentReminder(UUID businessId, UUID orderId) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new BusinessException("Order not found", "ORDER_NOT_FOUND"));
        
        if (order.getBalanceDue().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            return; // No balance due
        }
        
        User customer = userRepository.findById(order.getCustomerId())
            .orElseThrow(() -> new BusinessException("Customer not found", "CUSTOMER_NOT_FOUND"));
        
        Map<String, Object> data = new HashMap<>();
        data.put("orderId", order.getId().toString());
        data.put("orderNumber", order.getOrderNumber());
        data.put("balanceDue", order.getBalanceDue());
        data.put("customerName", customer.getFirstName());
        
        SendNotificationRequest request = new SendNotificationRequest();
        request.setRecipients(Collections.singletonList(customer.getId()));
        request.setType("PAYMENT");
        request.setTemplateData(data);
        
        // Send SMS reminder
        templateRepository.findActiveTemplate(
            businessId, Notification.NotificationType.PAYMENT, Notification.NotificationChannel.SMS)
            .ifPresent(t -> {
                request.setTemplateId(t.getId());
                request.setChannel("SMS");
                sendNotification(businessId, request);
            });
        
        // Send in-app notification
        request.setChannel("IN_APP");
        request.setTemplateId(null);
        request.setTitle("Payment Reminder");
        request.setBody(String.format("Your order %s has a balance due of $%s", 
            order.getOrderNumber(), order.getBalanceDue()));
        sendNotification(businessId, request);
    }
    
    @Transactional
    public void sendUnclaimedReminder(UUID businessId, UUID orderId, int daysUnclaimed) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new BusinessException("Order not found", "ORDER_NOT_FOUND"));
        
        User customer = userRepository.findById(order.getCustomerId())
            .orElseThrow(() -> new BusinessException("Customer not found", "CUSTOMER_NOT_FOUND"));
        
        Map<String, Object> data = new HashMap<>();
        data.put("orderId", order.getId().toString());
        data.put("orderNumber", order.getOrderNumber());
        data.put("daysUnclaimed", daysUnclaimed);
        data.put("customerName", customer.getFirstName());
        
        SendNotificationRequest request = new SendNotificationRequest();
        request.setRecipients(Collections.singletonList(customer.getId()));
        request.setType("REMINDER");
        request.setTemplateData(data);
        
        templateRepository.findActiveTemplate(
            businessId, Notification.NotificationType.REMINDER, Notification.NotificationChannel.SMS)
            .ifPresent(t -> {
                request.setTemplateId(t.getId());
                request.setChannel("SMS");
                sendNotification(businessId, request);
            });
    }
    
    @Transactional(readOnly = true)
public NotificationStatsDTO getDeliveryStats(UUID businessId, LocalDateTime startDate, LocalDateTime endDate) {
    List<NotificationLog> logs = logRepository.findByBusinessIdAndDateRange(businessId, startDate, endDate);
    
    long totalSent = logs.size();
    long totalDelivered = logs.stream().filter(l -> l.getStatus() == DeliveryStatus.DELIVERED).count();
    long totalFailed = logs.stream().filter(l -> l.getStatus() == DeliveryStatus.FAILED).count();
    double successRate = totalSent > 0 ? (totalDelivered * 100.0 / totalSent) : 0;
    
    Map<String, Long> byChannel = logs.stream()
        .collect(Collectors.groupingBy(
            l -> l.getChannel().toString(),
            Collectors.counting()
        ));
    
    Map<String, Long> byStatus = logs.stream()
        .collect(Collectors.groupingBy(
            l -> l.getStatus().toString(),
            Collectors.counting()
        ));
    
    return NotificationStatsDTO.builder()
        .totalSent(totalSent)
        .totalDelivered(totalDelivered)
        .totalFailed(totalFailed)
        .successRate(successRate)
        .byChannel(byChannel)
        .byStatus(byStatus)
        .build();
}

@Transactional
public void deleteTemplate(UUID templateId) {
    NotificationTemplate template = templateRepository.findById(templateId)
        .orElseThrow(() -> new BusinessException("Template not found", "TEMPLATE_NOT_FOUND"));
    templateRepository.delete(template);
}
    
    // ==================== Helper Methods ====================
    
    private NotificationDTO mapToDTO(Notification notification) {
        return NotificationDTO.builder()
            .id(notification.getId())
            .userId(notification.getUserId())
            .type(notification.getType().toString())
            .channel(notification.getChannel().toString())
            .title(notification.getTitle())
            .body(notification.getBody())
            .data(notification.getData())
            .status(notification.getStatus().toString())
            .priority(notification.getPriority().toString())
            .scheduledFor(notification.getScheduledFor())
            .createdAt(notification.getCreatedAt())
            .deliveredAt(notification.getDeliveredAt())
            .isRead(notification.getReadAt() != null)
            .build();
    }
    
    private NotificationTemplateDTO mapToTemplateDTO(NotificationTemplate template) {
        return NotificationTemplateDTO.builder()
            .id(template.getId())
            .name(template.getName())
            .description(template.getDescription())
            .type(template.getType().toString())
            .channel(template.getChannel().toString())
            .subject(template.getSubject())
            .titleTemplate(template.getTitleTemplate())
            .bodyTemplate(template.getBodyTemplate())
            .variables(template.getVariables())
            .isActive(template.getIsActive())
            .createdAt(template.getCreatedAt())
            .build();
    }
    
    private NotificationLogDTO mapToLogDTO(NotificationLog log) {
        return NotificationLogDTO.builder()
            .id(log.getId())
            .notificationId(log.getNotification() != null ? log.getNotification().getId() : null)
            .notificationType(log.getNotification() != null ? 
                log.getNotification().getType().toString() : null)
            .recipient(log.getRecipient())
            .channel(log.getChannel().toString())
            .status(log.getStatus().toString())
            .subject(log.getSubject())
            .errorMessage(log.getErrorMessage())
            .sentAt(log.getSentAt())
            .deliveredAt(log.getDeliveredAt())
            .retryCount(log.getRetryCount())
            .build();
    }
}
