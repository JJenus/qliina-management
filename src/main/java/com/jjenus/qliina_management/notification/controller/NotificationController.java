package com.jjenus.qliina_management.notification.controller;

import com.jjenus.qliina_management.common.CountDTO;
import com.jjenus.qliina_management.common.PageResponse;
import com.jjenus.qliina_management.common.SuccessResponse;
import com.jjenus.qliina_management.notification.dto.*;
import com.jjenus.qliina_management.notification.service.NotificationService;
import com.jjenus.qliina_management.notification.service.PushNotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Tag(name = "Notification Management", description = "Notification endpoints")
@RestController
@RequestMapping("/api/v1/{businessId}/notifications")
@RequiredArgsConstructor
public class NotificationController {
    
    private final NotificationService notificationService;
    private final PushNotificationService pushService;
    
    @Operation(summary = "Get notifications", description = "Get paginated list of notifications")
    @GetMapping
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'notification.view')")
    public ResponseEntity<PageResponse<NotificationDTO>> getNotifications(
            @PathVariable UUID businessId,
            Principal principal,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toDate,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        
        UUID userId = getCurrentUserId(principal);
        return ResponseEntity.ok(notificationService.getNotifications(
            businessId, userId, type, status, fromDate, toDate, pageable));
    }
    
    @Operation(summary = "Get unread count", description = "Get count of unread notifications")
    @GetMapping("/unread-count")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'notification.view')")
    public ResponseEntity<CountDTO> getUnreadCount(
            @PathVariable UUID businessId,
            Principal principal) {
        UUID userId = getCurrentUserId(principal);
        return ResponseEntity.ok(new CountDTO(notificationService.getUnreadCount(userId)));
    }
    
    @Operation(summary = "Mark as read", description = "Mark notifications as read")
    @PostMapping("/mark-read")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'notification.update')")
    public ResponseEntity<SuccessResponse> markAsRead(
            @PathVariable UUID businessId,
            Principal principal,
            @Valid @RequestBody MarkReadRequest request) {
        UUID userId = getCurrentUserId(principal);
        notificationService.markAsRead(request, userId);
        return ResponseEntity.ok(SuccessResponse.of("Notifications marked as read"));
    }
    
    @Operation(summary = "Send notification", description = "Send a new notification")
    @PostMapping("/send")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'notification.send')")
    public ResponseEntity<NotificationDTO> sendNotification(
            @PathVariable UUID businessId,
            @Valid @RequestBody SendNotificationRequest request) {
        return ResponseEntity.ok(notificationService.sendNotification(businessId, request));
    }
    
    @Operation(summary = "List templates", description = "Get all notification templates")
    @GetMapping("/templates")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'notification.view')")
    public ResponseEntity<List<NotificationTemplateDTO>> getTemplates(@PathVariable UUID businessId) {
        return ResponseEntity.ok(notificationService.getTemplates(businessId));
    }
    
    @Operation(summary = "Create template", description = "Create a notification template")
    @PostMapping("/templates")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'notification.manage')")
    public ResponseEntity<NotificationTemplateDTO> createTemplate(
            @PathVariable UUID businessId,
            @Valid @RequestBody CreateTemplateRequest request) {
        return ResponseEntity.ok(notificationService.createTemplate(businessId, request));
    }
    
    @Operation(summary = "Update template", description = "Update a notification template")
    @PutMapping("/templates/{templateId}")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'notification.manage')")
    public ResponseEntity<NotificationTemplateDTO> updateTemplate(
            @PathVariable UUID businessId,
            @PathVariable UUID templateId,
            @Valid @RequestBody UpdateTemplateRequest request) {
        return ResponseEntity.ok(notificationService.updateTemplate(templateId, request));
    }
    
    @Operation(summary = "Test notification", description = "Send a test notification")
    @PostMapping("/test")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'notification.send')")
    public ResponseEntity<SuccessResponse> testNotification(
            @PathVariable UUID businessId,
            @Valid @RequestBody TestNotificationRequest request) {
        notificationService.testNotification(businessId, request);
        return ResponseEntity.ok(SuccessResponse.of("Test notification sent"));
    }
    
    @Operation(summary = "Get notification logs", description = "Get delivery logs")
    @GetMapping("/logs")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'audit.view')")
    public ResponseEntity<PageResponse<NotificationLogDTO>> getNotificationLogs(
            @PathVariable UUID businessId,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toDate,
            @PageableDefault(size = 20, sort = "sentAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(notificationService.getNotificationLogs(
            businessId, channel, status, fromDate, toDate, pageable));
    }
    
    @Operation(summary = "Register device", description = "Register device for push notifications")
    @PostMapping("/devices/register")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'notification.update')")
    public ResponseEntity<UserDeviceDTO> registerDevice(
            @PathVariable UUID businessId,
            Principal principal,
            @Valid @RequestBody RegisterDeviceRequest request) {
        UUID userId = getCurrentUserId(principal);
        return ResponseEntity.ok(pushService.registerDevice(businessId, userId, request));
    }
    
    @Operation(summary = "Unregister device", description = "Unregister device from push notifications")
    @DeleteMapping("/devices/{deviceId}")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'notification.update')")
    public ResponseEntity<SuccessResponse> unregisterDevice(
            @PathVariable UUID businessId,
            Principal principal,
            @PathVariable String deviceId) {
        UUID userId = getCurrentUserId(principal);
        pushService.unregisterDevice(userId, deviceId);
        return ResponseEntity.ok(SuccessResponse.of("Device unregistered"));
    }
    
    private UUID getCurrentUserId(Principal principal) {
        // In real implementation, get from SecurityContext
        return UUID.fromString("00000000-0000-0000-0000-000000000000");
    }
}
