package com.jjenus.qliina_management.notification.controller;

import com.jjenus.qliina_management.common.CountDTO;
import com.jjenus.qliina_management.common.PageResponse;
import com.jjenus.qliina_management.common.SuccessResponse;
import com.jjenus.qliina_management.common.ErrorResponse;
import com.jjenus.qliina_management.notification.dto.*;
import com.jjenus.qliina_management.notification.service.NotificationOrchestrator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Tag(name = "Notification Management", description = "Complete notification management endpoints for in-app, email, SMS, and push notifications")
@RestController
@RequestMapping("/api/v1/{businessId}/notifications")
@RequiredArgsConstructor
public class NotificationController {
    
    private final NotificationOrchestrator notificationOrchestrator;

    // ==================== Notification Operations ====================
    
    @Operation(
        summary = "Get notifications",
        description = "Get paginated list of notifications for the authenticated user with optional filters"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved notifications"),
        @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid filter parameters",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'notification.view')")
    public ResponseEntity<PageResponse<NotificationDTO>> getNotifications(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Parameter(hidden = true)
            @AuthenticationPrincipal UserDetails userDetails,
            
            @Parameter(description = "Filter by notification type")
            @RequestParam(required = false) String type,
            
            @Parameter(description = "Filter by notification status")
            @RequestParam(required = false) String status,
            
            @Parameter(description = "Start date for filtering")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromDate,
            
            @Parameter(description = "End date for filtering")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toDate,
            
            @Parameter(description = "Pagination parameters")
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        
        UUID userId = getCurrentUserId(userDetails);
        return ResponseEntity.ok(notificationOrchestrator.getNotifications(
            businessId, userId, type, status, fromDate, toDate, pageable));
    }
    
    @Operation(
        summary = "Get unread count",
        description = "Get count of unread notifications for the authenticated user"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved count"),
        @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/unread-count")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'notification.view')")
    public ResponseEntity<CountDTO> getUnreadCount(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Parameter(hidden = true)
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = getCurrentUserId(userDetails);
        return ResponseEntity.ok(new CountDTO(notificationOrchestrator.getUnreadCount(userId)));
    }
    
    @Operation(
        summary = "Mark as read",
        description = "Mark specific notifications or all notifications as read"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Notifications marked as read successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/mark-read")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'notification.update')")
    public ResponseEntity<SuccessResponse> markAsRead(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Parameter(hidden = true)
            @AuthenticationPrincipal UserDetails userDetails,
            
            @Valid @RequestBody MarkReadRequest request) {
        UUID userId = getCurrentUserId(userDetails);
        notificationOrchestrator.markAsRead(request, userId);
        return ResponseEntity.ok(SuccessResponse.of("Notifications marked as read"));
    }
    
    @Operation(
        summary = "Send notification",
        description = "Send a new notification to specified recipients"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Notification sent successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid notification data",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Template not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/send")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'notification.send')")
    public ResponseEntity<NotificationDTO> sendNotification(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Valid @RequestBody SendNotificationRequest request) {
        return ResponseEntity.ok(notificationOrchestrator.sendNotification(businessId, request));
    }
    
    // ==================== Template Management ====================
    
    @Operation(
        summary = "List templates",
        description = "Get all notification templates for the business"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved templates"),
        @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/templates")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'notification.view')")
    public ResponseEntity<List<NotificationTemplateDTO>> getTemplates(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId) {
        return ResponseEntity.ok(notificationOrchestrator.getTemplates(businessId));
    }
    
    @Operation(
        summary = "Create template",
        description = "Create a new notification template"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Template created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid template data",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/templates")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'notification.manage')")
    public ResponseEntity<NotificationTemplateDTO> createTemplate(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Valid @RequestBody CreateTemplateRequest request) {
        return ResponseEntity.ok(notificationOrchestrator.createTemplate(businessId, request));
    }
    
    @Operation(
        summary = "Update template",
        description = "Update an existing notification template"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Template updated successfully"),
        @ApiResponse(responseCode = "404", description = "Template not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid update data",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping("/templates/{templateId}")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'notification.manage')")
    public ResponseEntity<NotificationTemplateDTO> updateTemplate(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Parameter(description = "Template ID", required = true)
            @PathVariable UUID templateId,
            
            @Valid @RequestBody UpdateTemplateRequest request) {
        return ResponseEntity.ok(notificationOrchestrator.updateTemplate(templateId, request));
    }
    
    @Operation(
        summary = "Delete template",
        description = "Delete a notification template"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Template deleted successfully"),
        @ApiResponse(responseCode = "404", description = "Template not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/templates/{templateId}")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'notification.manage')")
    public ResponseEntity<SuccessResponse> deleteTemplate(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Parameter(description = "Template ID", required = true)
            @PathVariable UUID templateId) {
        notificationOrchestrator.deleteTemplate(templateId);
        return ResponseEntity.ok(SuccessResponse.of("Template deleted successfully"));
    }
    
    // ==================== Test Operations ====================
    
    @Operation(
        summary = "Test notification",
        description = "Send a test notification to verify configuration"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Test notification sent"),
        @ApiResponse(responseCode = "400", description = "Invalid test request",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Template not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/test")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'notification.send')")
    public ResponseEntity<SuccessResponse> testNotification(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Valid @RequestBody TestNotificationRequest request) {
        notificationOrchestrator.testNotification(businessId, request);
        return ResponseEntity.ok(SuccessResponse.of("Test notification sent"));
    }
    
    // ==================== Logs & Analytics ====================
    
    @Operation(
        summary = "Get notification logs",
        description = "Get paginated list of notification delivery logs"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved logs"),
        @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/logs")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'audit.view')")
    public ResponseEntity<PageResponse<NotificationLogDTO>> getNotificationLogs(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Parameter(description = "Filter by channel (EMAIL, SMS, WHATSAPP, PUSH, IN_APP)")
            @RequestParam(required = false) String channel,
            
            @Parameter(description = "Filter by delivery status")
            @RequestParam(required = false) String status,
            
            @Parameter(description = "Start date for filtering")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromDate,
            
            @Parameter(description = "End date for filtering")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toDate,
            
            @Parameter(description = "Pagination parameters")
            @PageableDefault(size = 20, sort = "sentAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(notificationOrchestrator.getNotificationLogs(
            businessId, channel, status, fromDate, toDate, pageable));
    }
    
    @Operation(
        summary = "Get delivery statistics",
        description = "Get delivery statistics for notifications"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved statistics"),
        @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/stats")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'report.view.operational')")
    public ResponseEntity<NotificationStatsDTO> getDeliveryStats(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Parameter(description = "Start date", required = true)
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            
            @Parameter(description = "End date", required = true)
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        return ResponseEntity.ok(notificationOrchestrator.getDeliveryStats(businessId, startDate, endDate));
    }
    
    // ==================== Device Management ====================
    
    @Operation(
        summary = "Register device",
        description = "Register a device for push notifications"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Device registered successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid device data",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/devices/register")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'notification.update')")
    public ResponseEntity<UserDeviceDTO> registerDevice(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Parameter(hidden = true)
            @AuthenticationPrincipal UserDetails userDetails,
            
            @Valid @RequestBody RegisterDeviceRequest request) {
        UUID userId = getCurrentUserId(userDetails);
        return ResponseEntity.ok(notificationOrchestrator.registerDevice(businessId, userId, request));
    }
    
    @Operation(
        summary = "Unregister device",
        description = "Unregister a device from push notifications"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Device unregistered successfully"),
        @ApiResponse(responseCode = "404", description = "Device not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/devices/{deviceId}")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'notification.update')")
    public ResponseEntity<SuccessResponse> unregisterDevice(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Parameter(hidden = true)
            @AuthenticationPrincipal UserDetails userDetails,
            
            @Parameter(description = "Device ID", required = true)
            @PathVariable String deviceId) {
        UUID userId = getCurrentUserId(userDetails);
        notificationOrchestrator.unregisterDevice(userId, deviceId);
        return ResponseEntity.ok(SuccessResponse.of("Device unregistered"));
    }
    
    @Operation(
        summary = "List my devices",
        description = "Get all registered devices for the authenticated user"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved devices"),
        @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/devices")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'notification.view')")
    public ResponseEntity<List<UserDeviceDTO>> getMyDevices(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Parameter(hidden = true)
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = getCurrentUserId(userDetails);
        return ResponseEntity.ok(notificationOrchestrator.getMyDevices(userId));
    }
    
    private UUID getCurrentUserId(UserDetails userDetails) {
        // In real implementation, fetch from user repository
        return UUID.fromString("00000000-0000-0000-0000-000000000000");
    }
}