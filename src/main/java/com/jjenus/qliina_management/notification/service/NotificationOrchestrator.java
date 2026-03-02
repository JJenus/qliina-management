package com.jjenus.qliina_management.notification.service;

import com.jjenus.qliina_management.common.BusinessException;
import com.jjenus.qliina_management.common.PageResponse;
import com.jjenus.qliina_management.notification.dto.*;
import com.jjenus.qliina_management.notification.model.Notification;
import com.jjenus.qliina_management.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationOrchestrator {
    
    private final NotificationRepository notificationRepository;
    private final NotificationSender notificationSender;
    private final NotificationTemplateService templateService;
    private final NotificationLogService logService;
    private final NotificationDeviceService deviceService;
    
    // ==================== Notification Operations ====================
    
    @Transactional(readOnly = true)
    public PageResponse<NotificationDTO> getNotifications(UUID businessId, UUID userId, 
                                                            String type, String status,
                                                            LocalDateTime fromDate, LocalDateTime toDate,
                                                            Pageable pageable) {
        Notification.NotificationType notificationType = type != null ? 
            Notification.NotificationType.valueOf(type) : null;
        Notification.NotificationStatus notificationStatus = status != null ?
            Notification.NotificationStatus.valueOf(status) : null;
        
        var page = notificationRepository.findByFilters(
            businessId, userId, notificationType, notificationStatus, fromDate, toDate, pageable);
        
        return PageResponse.from(page.map(this::mapToDTO));
    }
    
    @Transactional(readOnly = true)
    public Long getUnreadCount(UUID userId) {
        return notificationRepository.countUnreadByUserId(userId);
    }
    
    @Transactional
    public void markAsRead(MarkReadRequest request, UUID userId) {
        if (Boolean.TRUE.equals(request.getMarkAll())) {
            notificationRepository.markAllAsRead(userId, LocalDateTime.now());
        } else if (request.getNotificationIds() != null) {
            request.getNotificationIds().forEach(id -> 
                notificationRepository.markAsRead(id, LocalDateTime.now()));
        }
    }
    
    @Transactional
    public NotificationDTO sendNotification(UUID businessId, SendNotificationRequest request) {
        List<Notification> notifications = notificationSender.prepareNotifications(businessId, request);
        
        if (request.getScheduledFor() == null || request.getScheduledFor().isBefore(LocalDateTime.now())) {
            notifications.forEach(notification -> 
                notificationSender.deliver(notification.getId()));
        }
        
        return notifications.isEmpty() ? null : mapToDTO(notifications.get(0));
    }
    
    // ==================== Template Operations ====================
    
    @Transactional(readOnly = true)
    public List<NotificationTemplateDTO> getTemplates(UUID businessId) {
        return templateService.getTemplates(businessId);
    }
    
    @Transactional
    public NotificationTemplateDTO createTemplate(UUID businessId, CreateTemplateRequest request) {
        return templateService.createTemplate(businessId, request);
    }
    
    @Transactional
    public NotificationTemplateDTO updateTemplate(UUID templateId, UpdateTemplateRequest request) {
        return templateService.updateTemplate(templateId, request);
    }
    
    @Transactional
    public void deleteTemplate(UUID templateId) {
        templateService.deleteTemplate(templateId);
    }
    
    // ==================== Device Operations ====================
    
    @Transactional
    public UserDeviceDTO registerDevice(UUID businessId, UUID userId, RegisterDeviceRequest request) {
        return deviceService.registerDevice(businessId, userId, request);
    }
    
    @Transactional
    public void unregisterDevice(UUID userId, String deviceId) {
        deviceService.unregisterDevice(userId, deviceId);
    }
    
    @Transactional(readOnly = true)
    public List<UserDeviceDTO> getMyDevices(UUID userId) {
        return deviceService.getUserDevices(userId);
    }
    
    // ==================== Logs & Analytics ====================
    
    @Transactional(readOnly = true)
    public PageResponse<NotificationLogDTO> getNotificationLogs(UUID businessId, String channel,
                                                                  String status, LocalDateTime fromDate,
                                                                  LocalDateTime toDate, Pageable pageable) {
        return logService.getNotificationLogs(businessId, channel, status, fromDate, toDate, pageable);
    }
    
    @Transactional(readOnly = true)
    public NotificationStatsDTO getDeliveryStats(UUID businessId, LocalDateTime startDate, LocalDateTime endDate) {
        return logService.getDeliveryStats(businessId, startDate, endDate);
    }
    
    // ==================== Test Operations ====================
    
    @Transactional
    public void testNotification(UUID businessId, TestNotificationRequest request) {
        notificationSender.sendTest(businessId, request);
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
}
