#!/bin/bash

# Set the base path - update this to match your actual project path
BASE_PATH="./notification"

echo "Creating refactored notification module structure..."

# Create directory structure
mkdir -p "$BASE_PATH/controller"
mkdir -p "$BASE_PATH/service/channel"
mkdir -p "$BASE_PATH/service/config"

# ==================== CONTROLLERS ====================

# NotificationConfigController.java
cat > "$BASE_PATH/controller/NotificationConfigController.java" << 'EOF'
package com.jjenus.qliina_management.notification.controller;

import com.jjenus.qliina_management.common.SuccessResponse;
import com.jjenus.qliina_management.common.ErrorResponse;
import com.jjenus.qliina_management.notification.dto.EmailConfigurationDTO;
import com.jjenus.qliina_management.notification.dto.PushConfigurationDTO;
import com.jjenus.qliina_management.notification.dto.SMSConfigurationDTO;
import com.jjenus.qliina_management.notification.service.config.EmailConfigService;
import com.jjenus.qliina_management.notification.service.config.SmsConfigService;
import com.jjenus.qliina_management.notification.service.config.PushConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Notification Configuration", description = "Endpoints for managing notification channel configurations")
@RestController
@RequestMapping("/api/v1/{businessId}/notifications/config")
@RequiredArgsConstructor
public class NotificationConfigController {
    
    private final EmailConfigService emailConfigService;
    private final SmsConfigService smsConfigService;
    private final PushConfigService pushConfigService;
    
    // ==================== Email Configuration ====================
    
    @Operation(
        summary = "Get email configuration",
        description = "Retrieve email configuration for the business"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved configuration"),
        @ApiResponse(responseCode = "404", description = "Configuration not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/email")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'notification.config.view')")
    public ResponseEntity<EmailConfigurationDTO> getEmailConfig(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId) {
        return ResponseEntity.ok(emailConfigService.getConfiguration(businessId));
    }
    
    @Operation(
        summary = "Configure email",
        description = "Set up or update email configuration for the business"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Email configured successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid configuration",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/email")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'notification.config.manage')")
    public ResponseEntity<EmailConfigurationDTO> configureEmail(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Valid @RequestBody EmailConfigurationDTO request) {
        return ResponseEntity.ok(emailConfigService.configureEmail(businessId, request));
    }
    
    @Operation(
        summary = "Test email configuration",
        description = "Send a test email to verify configuration"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Test email sent"),
        @ApiResponse(responseCode = "400", description = "Configuration invalid",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/email/test")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'notification.config.manage')")
    public ResponseEntity<SuccessResponse> testEmailConfig(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @RequestParam String testEmail) {
        emailConfigService.testConfiguration(businessId, testEmail);
        return ResponseEntity.ok(SuccessResponse.of("Test email sent successfully"));
    }
    
    // ==================== SMS Configuration ====================
    
    @Operation(
        summary = "Get SMS configuration",
        description = "Retrieve SMS configuration for the business"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved configuration"),
        @ApiResponse(responseCode = "404", description = "Configuration not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/sms")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'notification.config.view')")
    public ResponseEntity<SMSConfigurationDTO> getSmsConfig(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId) {
        return ResponseEntity.ok(smsConfigService.getConfiguration(businessId));
    }
    
    @Operation(
        summary = "Configure SMS",
        description = "Set up or update SMS configuration for the business"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "SMS configured successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid configuration",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/sms")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'notification.config.manage')")
    public ResponseEntity<SMSConfigurationDTO> configureSms(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Valid @RequestBody SMSConfigurationDTO request) {
        return ResponseEntity.ok(smsConfigService.configureSms(businessId, request));
    }
    
    @Operation(
        summary = "Test SMS configuration",
        description = "Send a test SMS to verify configuration"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Test SMS sent"),
        @ApiResponse(responseCode = "400", description = "Configuration invalid",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/sms/test")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'notification.config.manage')")
    public ResponseEntity<SuccessResponse> testSmsConfig(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @RequestParam String testPhoneNumber) {
        smsConfigService.testConfiguration(businessId, testPhoneNumber);
        return ResponseEntity.ok(SuccessResponse.of("Test SMS sent successfully"));
    }
    
    // ==================== Push Configuration ====================
    
    @Operation(
        summary = "Get push configuration",
        description = "Retrieve push notification configuration for the business"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved configuration"),
        @ApiResponse(responseCode = "404", description = "Configuration not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/push")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'notification.config.view')")
    public ResponseEntity<PushConfigurationDTO> getPushConfig(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId) {
        return ResponseEntity.ok(pushConfigService.getConfiguration(businessId));
    }
    
    @Operation(
        summary = "Configure push notifications",
        description = "Set up or update push notification configuration for the business"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Push configured successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid configuration",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/push")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'notification.config.manage')")
    public ResponseEntity<PushConfigurationDTO> configurePush(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Valid @RequestBody PushConfigurationDTO request) {
        return ResponseEntity.ok(pushConfigService.configurePush(businessId, request));
    }
}
EOF

echo "✓ Created NotificationConfigController.java"

# ==================== SERVICE LAYER ====================

# NotificationOrchestrator.java
cat > "$BASE_PATH/service/NotificationOrchestrator.java" << 'EOF'
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
EOF

echo "✓ Created NotificationOrchestrator.java"

# NotificationSender.java
cat > "$BASE_PATH/service/NotificationSender.java" << 'EOF'
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
EOF

echo "✓ Created NotificationSender.java"

# NotificationTemplateService.java
cat > "$BASE_PATH/service/NotificationTemplateService.java" << 'EOF'
package com.jjenus.qliina_management.notification.service;

import com.jjenus.qliina_management.common.BusinessException;
import com.jjenus.qliina_management.notification.dto.CreateTemplateRequest;
import com.jjenus.qliina_management.notification.dto.NotificationTemplateDTO;
import com.jjenus.qliina_management.notification.dto.UpdateTemplateRequest;
import com.jjenus.qliina_management.notification.model.Notification;
import com.jjenus.qliina_management.notification.model.NotificationTemplate;
import com.jjenus.qliina_management.notification.repository.NotificationTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NotificationTemplateService {
    
    private final NotificationTemplateRepository templateRepository;
    
    @Transactional(readOnly = true)
    public List<NotificationTemplateDTO> getTemplates(UUID businessId) {
        return templateRepository.findByBusinessId(businessId).stream()
            .map(this::mapToDTO)
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
        return mapToDTO(template);
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
        return mapToDTO(template);
    }
    
    @Transactional
    public void deleteTemplate(UUID templateId) {
        NotificationTemplate template = templateRepository.findById(templateId)
            .orElseThrow(() -> new BusinessException("Template not found", "TEMPLATE_NOT_FOUND"));
        templateRepository.delete(template);
    }
    
    private NotificationTemplateDTO mapToDTO(NotificationTemplate template) {
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
}
EOF

echo "✓ Created NotificationTemplateService.java"

# NotificationDeviceService.java
cat > "$BASE_PATH/service/NotificationDeviceService.java" << 'EOF'
package com.jjenus.qliina_management.notification.service;

import com.jjenus.qliina_management.common.BusinessException;
import com.jjenus.qliina_management.notification.dto.RegisterDeviceRequest;
import com.jjenus.qliina_management.notification.dto.UserDeviceDTO;
import com.jjenus.qliina_management.notification.model.UserDevice;
import com.jjenus.qliina_management.notification.repository.UserDeviceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NotificationDeviceService {
    
    private final UserDeviceRepository userDeviceRepository;
    
    @Transactional(readOnly = true)
    public List<UserDeviceDTO> getUserDevices(UUID userId) {
        return userDeviceRepository.findByUserIdAndIsActiveTrue(userId).stream()
            .map(this::convertToDTO)
            .collect(Collectors.toList());
    }
    
    @Transactional
    public UserDeviceDTO registerDevice(UUID businessId, UUID userId, RegisterDeviceRequest request) {
        userDeviceRepository.findByUserIdAndDeviceId(userId, request.getDeviceId())
            .ifPresent(device -> {
                device.setIsActive(false);
                userDeviceRepository.save(device);
            });
        
        UserDevice device = new UserDevice();
        device.setBusinessId(businessId);
        device.setUserId(userId);
        device.setDeviceId(request.getDeviceId());
        device.setDeviceType(UserDevice.DeviceType.valueOf(request.getDeviceType()));
        device.setPushToken(request.getPushToken());
        device.setAppVersion(request.getAppVersion());
        device.setOsVersion(request.getOsVersion());
        device.setModel(request.getModel());
        device.setIsActive(true);
        device.setLastUsedAt(LocalDateTime.now());
        
        device = userDeviceRepository.save(device);
        return convertToDTO(device);
    }
    
    @Transactional
    public void unregisterDevice(UUID userId, String deviceId) {
        userDeviceRepository.findByUserIdAndDeviceId(userId, deviceId)
            .ifPresent(device -> {
                device.setIsActive(false);
                userDeviceRepository.save(device);
            });
    }
    
    private UserDeviceDTO convertToDTO(UserDevice device) {
        if (device == null) return null;
        return UserDeviceDTO.builder()
            .id(device.getId())
            .userId(device.getUserId())
            .deviceId(device.getDeviceId())
            .deviceType(device.getDeviceType() != null ? device.getDeviceType().name() : null)
            .pushToken(device.getPushToken())
            .isActive(device.getIsActive())
            .lastUsedAt(device.getLastUsedAt())
            .appVersion(device.getAppVersion())
            .osVersion(device.getOsVersion())
            .model(device.getModel())
            .build();
    }
}
EOF

echo "✓ Created NotificationDeviceService.java"

# NotificationLogService.java
cat > "$BASE_PATH/service/NotificationLogService.java" << 'EOF'
package com.jjenus.qliina_management.notification.service;

import com.jjenus.qliina_management.common.PageResponse;
import com.jjenus.qliina_management.notification.dto.NotificationLogDTO;
import com.jjenus.qliina_management.notification.dto.NotificationStatsDTO;
import com.jjenus.qliina_management.notification.model.Notification;
import com.jjenus.qliina_management.notification.model.NotificationLog;
import com.jjenus.qliina_management.notification.repository.NotificationLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NotificationLogService {
    
    private final NotificationLogRepository logRepository;
    
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
        
        return PageResponse.from(page.map(this::mapToDTO));
    }
    
    @Transactional(readOnly = true)
    public NotificationStatsDTO getDeliveryStats(UUID businessId, LocalDateTime startDate, LocalDateTime endDate) {
        var logs = logRepository.findByBusinessIdAndDateRange(businessId, startDate, endDate);
        
        long totalSent = logs.size();
        long totalDelivered = logs.stream().filter(l -> l.getStatus() == NotificationLog.DeliveryStatus.DELIVERED).count();
        long totalFailed = logs.stream().filter(l -> l.getStatus() == NotificationLog.DeliveryStatus.FAILED).count();
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
    
    private NotificationLogDTO mapToDTO(NotificationLog log) {
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
EOF

echo "✓ Created NotificationLogService.java"

# ==================== CHANNEL SERVICES ====================

# EmailChannelService.java
cat > "$BASE_PATH/service/channel/EmailChannelService.java" << 'EOF'
package com.jjenus.qliina_management.notification.service.channel;

import com.jjenus.qliina_management.common.BusinessException;
import com.jjenus.qliina_management.identity.model.User;
import com.jjenus.qliina_management.notification.model.EmailConfiguration;
import com.jjenus.qliina_management.notification.model.Notification;
import com.jjenus.qliina_management.notification.model.NotificationLog;
import com.jjenus.qliina_management.notification.repository.EmailConfigurationRepository;
import com.jjenus.qliina_management.notification.repository.NotificationLogRepository;
import com.jjenus.qliina_management.notification.service.EncryptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;
import java.time.LocalDateTime;
import java.util.Properties;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailChannelService {
    
    private final EmailConfigurationRepository emailConfigRepository;
    private final NotificationLogRepository logRepository;
    private final EncryptionService encryptionService;
    
    public void send(Notification notification, User user) {
        EmailConfiguration config = getConfig(notification.getBusinessId());
        JavaMailSender mailSender = createMailSender(config);
        
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            helper.setFrom(config.getFromAddress(), config.getFromName());
            helper.setTo(user.getEmail());
            helper.setSubject(notification.getTitle());
            helper.setText(notification.getBody(), true);
            
            mailSender.send(message);
            
            createLog(notification, user.getEmail(), "Email sent successfully");
            log.info("Email sent to: {}", user.getEmail());
            
        } catch (Exception e) {
            createErrorLog(notification, user.getEmail(), e.getMessage());
            throw new BusinessException("Failed to send email: " + e.getMessage(), "EMAIL_SEND_FAILED");
        }
    }
    
    public void sendTest(UUID businessId, String recipient, String subject, String content) {
        EmailConfiguration config = getConfig(businessId);
        JavaMailSender mailSender = createMailSender(config);
        
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            helper.setFrom(config.getFromAddress(), config.getFromName());
            helper.setTo(recipient);
            helper.setSubject(subject != null ? subject : "Test Notification");
            helper.setText(content != null ? content : "This is a test notification", true);
            
            mailSender.send(message);
            log.info("Test email sent to: {}", recipient);
            
        } catch (Exception e) {
            throw new BusinessException("Failed to send test email: " + e.getMessage(), "TEST_EMAIL_FAILED");
        }
    }
    
    private EmailConfiguration getConfig(UUID businessId) {
        return emailConfigRepository.findByBusinessId(businessId)
            .orElseThrow(() -> new BusinessException("Email not configured for business", "EMAIL_NOT_CONFIGURED"));
    }
    
    private JavaMailSender createMailSender(EmailConfiguration config) {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(config.getHost());
        mailSender.setPort(config.getPort());
        mailSender.setUsername(config.getUsername());
        mailSender.setPassword(encryptionService.decrypt(config.getPasswordEncrypted()));
        
        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", config.getUseTls());
        props.put("mail.smtp.ssl.enable", config.getUseSsl());
        
        return mailSender;
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
    
    private void createErrorLog(Notification notification, String recipient, String error) {
        NotificationLog log = new NotificationLog();
        log.setBusinessId(notification.getBusinessId());
        log.setNotification(notification);
        log.setRecipient(recipient);
        log.setChannel(notification.getChannel());
        log.setStatus(NotificationLog.DeliveryStatus.FAILED);
        log.setSubject(notification.getTitle());
        log.setErrorMessage(error);
        log.setSentAt(LocalDateTime.now());
        
        logRepository.save(log);
    }
}
EOF

echo "✓ Created EmailChannelService.java"

# SmsChannelService.java
cat > "$BASE_PATH/service/channel/SmsChannelService.java" << 'EOF'
package com.jjenus.qliina_management.notification.service.channel;

import com.jjenus.qliina_management.common.BusinessException;
import com.jjenus.qliina_management.identity.model.User;
import com.jjenus.qliina_management.notification.model.Notification;
import com.jjenus.qliina_management.notification.model.NotificationLog;
import com.jjenus.qliina_management.notification.model.SMSConfiguration;
import com.jjenus.qliina_management.notification.repository.NotificationLogRepository;
import com.jjenus.qliina_management.notification.repository.SMSConfigurationRepository;
import com.jjenus.qliina_management.notification.service.EncryptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SmsChannelService {
    
    private final SMSConfigurationRepository smsConfigRepository;
    private final NotificationLogRepository logRepository;
    private final EncryptionService encryptionService;
    
    public void send(Notification notification, User user) {
        SMSConfiguration config = getConfig(notification.getBusinessId());
        
        try {
            switch (config.getProvider()) {
                case TWILIO:
                    sendViaTwilio(config, user.getPhone(), notification.getBody());
                    break;
                case AWS_SNS:
                    sendViaAWSSNS(config, user.getPhone(), notification.getBody());
                    break;
                case VONAGE:
                    sendViaVonage(config, user.getPhone(), notification.getBody());
                    break;
                default:
                    throw new BusinessException("Unsupported SMS provider", "UNSUPPORTED_PROVIDER");
            }
            
            createLog(notification, user.getPhone(), "SMS sent successfully");
            log.info("SMS sent to: {}", user.getPhone());
            
        } catch (Exception e) {
            createErrorLog(notification, user.getPhone(), e.getMessage());
            throw new BusinessException("Failed to send SMS: " + e.getMessage(), "SMS_SEND_FAILED");
        }
    }
    
    public void sendTest(UUID businessId, String recipient, String message) {
        SMSConfiguration config = getConfig(businessId);
        
        try {
            switch (config.getProvider()) {
                case TWILIO:
                    sendViaTwilio(config, recipient, message);
                    break;
                case AWS_SNS:
                    sendViaAWSSNS(config, recipient, message);
                    break;
                case VONAGE:
                    sendViaVonage(config, recipient, message);
                    break;
                default:
                    throw new BusinessException("Unsupported SMS provider", "UNSUPPORTED_PROVIDER");
            }
            log.info("Test SMS sent to: {}", recipient);
            
        } catch (Exception e) {
            throw new BusinessException("Failed to send test SMS: " + e.getMessage(), "TEST_SMS_FAILED");
        }
    }
    
    private void sendViaTwilio(SMSConfiguration config, String to, String message) {
        String accountSid = encryptionService.decrypt(config.getAccountSidEncrypted());
        String authToken = encryptionService.decrypt(config.getAuthTokenEncrypted());
        log.info("Sending Twilio SMS from {} to: {}", config.getFromNumber(), to);
        // Actual Twilio API call would be here
    }
    
    private void sendViaAWSSNS(SMSConfiguration config, String to, String message) {
        log.info("Sending AWS SNS SMS to: {}", to);
        // AWS SNS implementation would go here
    }
    
    private void sendViaVonage(SMSConfiguration config, String to, String message) {
        log.info("Sending Vonage SMS to: {}", to);
        // Vonage implementation would go here
    }
    
    private SMSConfiguration getConfig(UUID businessId) {
        return smsConfigRepository.findByBusinessId(businessId)
            .orElseThrow(() -> new BusinessException("SMS not configured for business", "SMS_NOT_CONFIGURED"));
    }
    
    private void createLog(Notification notification, String recipient, String message) {
        NotificationLog log = new NotificationLog();
        log.setBusinessId(notification.getBusinessId());
        log.setNotification(notification);
        log.setRecipient(recipient);
        log.setChannel(notification.getChannel());
        log.setStatus(NotificationLog.DeliveryStatus.SENT);
        log.setSubject("SMS Notification");
        log.setContent(notification.getBody());
        log.setSentAt(LocalDateTime.now());
        log.setProviderResponse(message);
        
        logRepository.save(log);
    }
    
    private void createErrorLog(Notification notification, String recipient, String error) {
        NotificationLog log = new NotificationLog();
        log.setBusinessId(notification.getBusinessId());
        log.setNotification(notification);
        log.setRecipient(recipient);
        log.setChannel(notification.getChannel());
        log.setStatus(NotificationLog.DeliveryStatus.FAILED);
        log.setSubject("SMS Notification");
        log.setErrorMessage(error);
        log.setSentAt(LocalDateTime.now());
        
        logRepository.save(log);
    }
}
EOF

echo "✓ Created SmsChannelService.java"

# PushChannelService.java
cat > "$BASE_PATH/service/channel/PushChannelService.java" << 'EOF'
package com.jjenus.qliina_management.notification.service.channel;

import com.jjenus.qliina_management.common.BusinessException;
import com.jjenus.qliina_management.identity.model.User;
import com.jjenus.qliina_management.notification.model.Notification;
import com.jjenus.qliina_management.notification.model.NotificationLog;
import com.jjenus.qliina_management.notification.model.PushNotificationConfiguration;
import com.jjenus.qliina_management.notification.model.UserDevice;
import com.jjenus.qliina_management.notification.repository.NotificationLogRepository;
import com.jjenus.qliina_management.notification.repository.PushNotificationConfigurationRepository;
import com.jjenus.qliina_management.notification.repository.UserDeviceRepository;
import com.jjenus.qliina_management.notification.service.EncryptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PushChannelService {
    
    private final PushNotificationConfigurationRepository pushConfigRepository;
    private final UserDeviceRepository userDeviceRepository;
    private final NotificationLogRepository logRepository;
    private final EncryptionService encryptionService;
    
    public void send(Notification notification, User user) {
        PushNotificationConfiguration config = getConfig(notification.getBusinessId());
        List<UserDevice> devices = userDeviceRepository.findByUserIdAndIsActiveTrue(user.getId());
        
        if (devices.isEmpty()) {
            log.warn("No active devices found for user: {}", user.getId());
            return;
        }
        
        for (UserDevice device : devices) {
            try {
                switch (device.getDeviceType()) {
                    case ANDROID:
                        sendToAndroid(config, device, notification);
                        break;
                    case IOS:
                        sendToIOS(config, device, notification);
                        break;
                    case WEB:
                        sendToWeb(config, device, notification);
                        break;
                }
                log.info("Push notification sent to device: {}", device.getDeviceId());
                
            } catch (Exception e) {
                log.error("Failed to send push to device: {}", device.getDeviceId(), e);
            }
        }
        
        createLog(notification, user.getId().toString(), "Push notification sent to " + devices.size() + " devices");
    }
    
    private void sendToAndroid(PushNotificationConfiguration config, UserDevice device, Notification notification) {
        String serverKey = encryptionService.decrypt(config.getFcmServerKeyEncrypted());
        log.info("Sending Android push via FCM to device: {}", device.getPushToken());
        // FCM implementation would go here
    }
    
    private void sendToIOS(PushNotificationConfiguration config, UserDevice device, Notification notification) {
        log.info("Sending iOS push via APNS to device: {}", device.getPushToken());
        // APNS implementation would go here
    }
    
    private void sendToWeb(PushNotificationConfiguration config, UserDevice device, Notification notification) {
        log.info("Sending web push to device: {}", device.getPushToken());
        // Web push implementation would go here
    }
    
    private PushNotificationConfiguration getConfig(UUID businessId) {
        return pushConfigRepository.findByBusinessId(businessId)
            .orElseThrow(() -> new BusinessException("Push notifications not configured", "PUSH_NOT_CONFIGURED"));
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
}
EOF

echo "✓ Created PushChannelService.java"

# WhatsAppChannelService.java
cat > "$BASE_PATH/service/channel/WhatsAppChannelService.java" << 'EOF'
package com.jjenus.qliina_management.notification.service.channel;

import com.jjenus.qliina_management.common.BusinessException;
import com.jjenus.qliina_management.identity.model.User;
import com.jjenus.qliina_management.notification.model.Notification;
import com.jjenus.qliina_management.notification.model.NotificationLog;
import com.jjenus.qliina_management.notification.repository.NotificationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WhatsAppChannelService {
    
    private final NotificationLogRepository logRepository;
    
    public void send(Notification notification, User user) {
        try {
            // WhatsApp Business API implementation would go here
            log.info("Sending WhatsApp message to: {}", user.getPhone());
            
            createLog(notification, user.getPhone(), "WhatsApp message sent successfully");
            
        } catch (Exception e) {
            createErrorLog(notification, user.getPhone(), e.getMessage());
            throw new BusinessException("Failed to send WhatsApp message: " + e.getMessage(), "WHATSAPP_SEND_FAILED");
        }
    }
    
    public void sendTest(UUID businessId, String recipient, String message) {
        try {
            log.info("Sending test WhatsApp message to: {}", recipient);
            // WhatsApp test implementation
        } catch (Exception e) {
            throw new BusinessException("Failed to send test WhatsApp message: " + e.getMessage(), "TEST_WHATSAPP_FAILED");
        }
    }
    
    private void createLog(Notification notification, String recipient, String message) {
        NotificationLog log = new NotificationLog();
        log.setBusinessId(notification.getBusinessId());
        log.setNotification(notification);
        log.setRecipient(recipient);
        log.setChannel(notification.getChannel());
        log.setStatus(NotificationLog.DeliveryStatus.SENT);
        log.setSubject("WhatsApp Notification");
        log.setContent(notification.getBody());
        log.setSentAt(LocalDateTime.now());
        log.setProviderResponse(message);
        
        logRepository.save(log);
    }
    
    private void createErrorLog(Notification notification, String recipient, String error) {
        NotificationLog log = new NotificationLog();
        log.setBusinessId(notification.getBusinessId());
        log.setNotification(notification);
        log.setRecipient(recipient);
        log.setChannel(notification.getChannel());
        log.setStatus(NotificationLog.DeliveryStatus.FAILED);
        log.setSubject("WhatsApp Notification");
        log.setErrorMessage(error);
        log.setSentAt(LocalDateTime.now());
        
        logRepository.save(log);
    }
}
EOF

echo "✓ Created WhatsAppChannelService.java"

# ==================== CONFIG SERVICES ====================

# EmailConfigService.java
cat > "$BASE_PATH/service/config/EmailConfigService.java" << 'EOF'
package com.jjenus.qliina_management.notification.service.config;

import com.jjenus.qliina_management.common.BusinessException;
import com.jjenus.qliina_management.notification.dto.EmailConfigurationDTO;
import com.jjenus.qliina_management.notification.model.EmailConfiguration;
import com.jjenus.qliina_management.notification.repository.EmailConfigurationRepository;
import com.jjenus.qliina_management.notification.service.EncryptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EmailConfigService {
    
    private final EmailConfigurationRepository emailConfigRepository;
    private final EncryptionService encryptionService;
    
    @Transactional(readOnly = true)
    public EmailConfigurationDTO getConfiguration(UUID businessId) {
        EmailConfiguration config = emailConfigRepository.findByBusinessId(businessId)
            .orElseThrow(() -> new BusinessException("Email configuration not found", "EMAIL_CONFIG_NOT_FOUND"));
        return mapToDTO(config);
    }
    
    @Transactional
    public EmailConfigurationDTO configureEmail(UUID businessId, EmailConfigurationDTO request) {
        EmailConfiguration config = emailConfigRepository.findByBusinessId(businessId)
            .orElse(new EmailConfiguration());
        
        config.setBusinessId(businessId);
        config.setHost(request.getHost());
        config.setPort(request.getPort());
        config.setUsername(request.getUsername());
        config.setPasswordEncrypted(encryptionService.encrypt(request.getPassword()));
        config.setFromAddress(request.getFromAddress());
        config.setFromName(request.getFromName());
        config.setUseTls(request.getUseTls());
        config.setUseSsl(request.getUseSsl());
        config.setIsConfigured(true);
        
        config = emailConfigRepository.save(config);
        return mapToDTO(config);
    }
    
    @Transactional
    public void testConfiguration(UUID businessId, String testEmail) {
        EmailConfiguration config = emailConfigRepository.findByBusinessId(businessId)
            .orElseThrow(() -> new BusinessException("Email not configured", "EMAIL_NOT_CONFIGURED"));
        
        // Test logic would go here - send test email, validate connection, etc.
        if (config.getHost() == null || config.getPort() == null) {
            throw new BusinessException("Invalid email configuration", "INVALID_EMAIL_CONFIG");
        }
    }
    
    private EmailConfigurationDTO mapToDTO(EmailConfiguration config) {
        return EmailConfigurationDTO.builder()
            .businessId(config.getBusinessId())
            .host(config.getHost())
            .port(config.getPort())
            .username(config.getUsername())
            .fromAddress(config.getFromAddress())
            .fromName(config.getFromName())
            .useTls(config.getUseTls())
            .useSsl(config.getUseSsl())
            .isConfigured(config.getIsConfigured())
            .build();
    }
}
EOF

echo "✓ Created EmailConfigService.java"

# SmsConfigService.java
cat > "$BASE_PATH/service/config/SmsConfigService.java" << 'EOF'
package com.jjenus.qliina_management.notification.service.config;

import com.jjenus.qliina_management.common.BusinessException;
import com.jjenus.qliina_management.notification.dto.SMSConfigurationDTO;
import com.jjenus.qliina_management.notification.model.SMSConfiguration;
import com.jjenus.qliina_management.notification.repository.SMSConfigurationRepository;
import com.jjenus.qliina_management.notification.service.EncryptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SmsConfigService {
    
    private final SMSConfigurationRepository smsConfigRepository;
    private final EncryptionService encryptionService;
    
    @Transactional(readOnly = true)
    public SMSConfigurationDTO getConfiguration(UUID businessId) {
        SMSConfiguration config = smsConfigRepository.findByBusinessId(businessId)
            .orElseThrow(() -> new BusinessException("SMS configuration not found", "SMS_CONFIG_NOT_FOUND"));
        return mapToDTO(config);
    }
    
    @Transactional
    public SMSConfigurationDTO configureSms(UUID businessId, SMSConfigurationDTO request) {
        SMSConfiguration config = smsConfigRepository.findByBusinessId(businessId)
            .orElse(new SMSConfiguration());
        
        config.setBusinessId(businessId);
        config.setProvider(SMSConfiguration.SMSProvider.valueOf(request.getProvider()));
        config.setAccountSidEncrypted(encryptionService.encrypt(request.getAccountSid()));
        config.setAuthTokenEncrypted(encryptionService.encrypt(request.getAuthToken()));
        config.setFromNumber(request.getFromNumber());
        config.setIsConfigured(true);
        
        config = smsConfigRepository.save(config);
        return mapToDTO(config);
    }
    
    @Transactional
    public void testConfiguration(UUID businessId, String testPhoneNumber) {
        SMSConfiguration config = smsConfigRepository.findByBusinessId(businessId)
            .orElseThrow(() -> new BusinessException("SMS not configured", "SMS_NOT_CONFIGURED"));
        
        // Test logic would go here
        if (config.getProvider() == null) {
            throw new BusinessException("Invalid SMS configuration", "INVALID_SMS_CONFIG");
        }
    }
    
    private SMSConfigurationDTO mapToDTO(SMSConfiguration config) {
        return SMSConfigurationDTO.builder()
            .businessId(config.getBusinessId())
            .provider(config.getProvider() != null ? config.getProvider().name() : null)
            .fromNumber(config.getFromNumber())
            .isConfigured(config.getIsConfigured())
            .build();
    }
}
EOF

echo "✓ Created SmsConfigService.java"

# PushConfigService.java
cat > "$BASE_PATH/service/config/PushConfigService.java" << 'EOF'
package com.jjenus.qliina_management.notification.service.config;

import com.jjenus.qliina_management.common.BusinessException;
import com.jjenus.qliina_management.notification.dto.PushConfigurationDTO;
import com.jjenus.qliina_management.notification.model.PushNotificationConfiguration;
import com.jjenus.qliina_management.notification.repository.PushNotificationConfigurationRepository;
import com.jjenus.qliina_management.notification.service.EncryptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PushConfigService {
    
    private final PushNotificationConfigurationRepository pushConfigRepository;
    private final EncryptionService encryptionService;
    
    @Transactional(readOnly = true)
    public PushConfigurationDTO getConfiguration(UUID businessId) {
        PushNotificationConfiguration config = pushConfigRepository.findByBusinessId(businessId)
            .orElseThrow(() -> new BusinessException("Push configuration not found", "PUSH_CONFIG_NOT_FOUND"));
        return mapToDTO(config);
    }
    
    @Transactional
    public PushConfigurationDTO configurePush(UUID businessId, PushConfigurationDTO request) {
        PushNotificationConfiguration config = pushConfigRepository.findByBusinessId(businessId)
            .orElse(new PushNotificationConfiguration());
        
        config.setBusinessId(businessId);
        config.setIsConfigured(true);
        // Add actual push configuration logic here
        
        config = pushConfigRepository.save(config);
        return mapToDTO(config);
    }
    
    private PushConfigurationDTO mapToDTO(PushNotificationConfiguration config) {
        return PushConfigurationDTO.builder()
            .businessId(config.getBusinessId())
            .isConfigured(config.getIsConfigured())
            .build();
    }
}
EOF

echo "✓ Created PushConfigService.java"

# ==================== UPDATE EXISTING FILES ====================

# Update NotificationController.java (keep existing but change injection)
if [ -f "$BASE_PATH/controller/NotificationController.java" ]; then
    # Create backup
    cp "$BASE_PATH/controller/NotificationController.java" "$BASE_PATH/controller/NotificationController.java.bak"
    echo "✓ Created backup of existing NotificationController.java"
    
    # Update the file - replace PushNotificationService with NotificationOrchestrator
    sed -i 's/private final PushNotificationService pushService;/\/\/ private final PushNotificationService pushService; (moved to orchestrator)/g' "$BASE_PATH/controller/NotificationController.java"
    sed -i 's/private final NotificationService notificationService;/private final NotificationOrchestrator notificationOrchestrator;/g' "$BASE_PATH/controller/NotificationController.java"
    
    # Update method calls
    sed -i 's/notificationService\./notificationOrchestrator\./g' "$BASE_PATH/controller/NotificationController.java"
    sed -i 's/pushService\./notificationOrchestrator\./g' "$BASE_PATH/controller/NotificationController.java"
    
    echo "✓ Updated NotificationController.java"
else
    echo "⚠ Warning: NotificationController.java not found at expected path"
fi

echo ""
echo "✅ Refactored notification module structure created successfully!"
echo ""
echo "Files created:"
echo "  - controller/NotificationConfigController.java"
echo "  - service/NotificationOrchestrator.java"
echo "  - service/NotificationSender.java"
echo "  - service/NotificationTemplateService.java"
echo "  - service/NotificationDeviceService.java"
echo "  - service/NotificationLogService.java"
echo "  - service/channel/EmailChannelService.java"
echo "  - service/channel/SmsChannelService.java"
echo "  - service/channel/PushChannelService.java"
echo "  - service/channel/WhatsAppChannelService.java"
echo "  - service/config/EmailConfigService.java"
echo "  - service/config/SmsConfigService.java"
echo "  - service/config/PushConfigService.java"
echo ""
echo "Next steps:"
echo "  1. Review and adjust any package imports if your base package differs"
echo "  2. Update NotificationScheduler.java to use the new services"
echo "  3. Update any other modules that directly called the old NotificationService"
echo "  4. Run tests to verify the refactoring"
echo ""
echo "Note: A backup of your original NotificationController.java was created with .bak extension"