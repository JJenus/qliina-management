package com.jjenus.qliina_management.notification.controller;

import com.jjenus.qliina_management.business.model.Business;
import com.jjenus.qliina_management.business.repository.BusinessRepository;
import com.jjenus.qliina_management.common.BusinessException;
import com.jjenus.qliina_management.identity.model.User;
import com.jjenus.qliina_management.identity.repository.UserRepository;
import com.jjenus.qliina_management.notification.dto.SendNotificationRequest;
import com.jjenus.qliina_management.notification.model.NotificationTemplate;
import com.jjenus.qliina_management.notification.repository.NotificationDeliveryRepository;
import com.jjenus.qliina_management.notification.repository.NotificationOutboxRepository;
import com.jjenus.qliina_management.notification.repository.NotificationRepository;
import com.jjenus.qliina_management.notification.repository.NotificationTemplateRepository;
import com.jjenus.qliina_management.notification.service.NotificationSender;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Platform-level notification administration:
 *   GET   /api/v1/admin/notification-health      — delivery/outbox health rollups
 *   GET   /api/v1/admin/notification-templates   — system templates (businessId IS NULL)
 *   PATCH /api/v1/admin/notification-templates/{id}/status — enable/disable a template
 *   POST  /api/v1/admin/broadcasts               — announcement to selected tenants or all
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminNotificationController {

    private final NotificationRepository notificationRepository;
    private final NotificationDeliveryRepository deliveryRepository;
    private final NotificationOutboxRepository outboxRepository;
    private final NotificationTemplateRepository templateRepository;
    private final BusinessRepository businessRepository;
    private final UserRepository userRepository;
    private final NotificationSender notificationSender;

    // ---------------------------------------------------------------------
    // Health
    // ---------------------------------------------------------------------

    @GetMapping("/notification-health")
    @PreAuthorize("""
        hasPermission(null, 'PLATFORM', 'platform.notifications.manage')
        or hasPermission(null, 'PLATFORM', 'platform.settings.manage')
    """)
    public ResponseEntity<Map<String, Object>> health() {
        LocalDateTime d7 = LocalDateTime.now().minusDays(7);

        Map<String, Long> notificationsByStatus = new LinkedHashMap<>();
        for (Object[] row : notificationRepository.countGroupedByStatus()) {
            notificationsByStatus.put(String.valueOf(row[0]), (Long) row[1]);
        }

        Map<String, Long> deliveriesByStatus = new LinkedHashMap<>();
        for (Object[] row : deliveryRepository.countGroupedByStatus()) {
            deliveriesByStatus.put(String.valueOf(row[0]), (Long) row[1]);
        }

        Map<String, Long> outboxByStatus = new LinkedHashMap<>();
        for (Object[] row : outboxRepository.countGroupedByStatus()) {
            outboxByStatus.put(String.valueOf(row[0]), (Long) row[1]);
        }

        long sent7d = notificationRepository.countByCreatedAtAfter(d7);
        long failed7d = notificationRepository.countByStatusAndCreatedAtAfter(
                com.jjenus.qliina_management.notification.model.Notification.NotificationStatus.FAILED, d7);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("notificationsByStatus", notificationsByStatus);
        result.put("deliveriesByStatus", deliveriesByStatus);
        result.put("outboxByStatus", outboxByStatus);
        result.put("createdLast7d", sent7d);
        result.put("failedLast7d", failed7d);
        result.put("failureRate7dPct", sent7d > 0 ? Math.round(failed7d * 10000.0 / sent7d) / 100.0 : 0);
        return ResponseEntity.ok(result);
    }

    // ---------------------------------------------------------------------
    // System templates
    // ---------------------------------------------------------------------

    @GetMapping("/notification-templates")
    @PreAuthorize("""
        hasPermission(null, 'PLATFORM', 'platform.notifications.manage')
        or hasPermission(null, 'PLATFORM', 'platform.settings.manage')
    """)
    public ResponseEntity<List<Map<String, Object>>> templates() {
        List<Map<String, Object>> out = templateRepository.findAll(Sort.by("name")).stream()
                .filter(t -> t.getBusinessId() == null)
                .map(t -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", t.getId());
                    m.put("name", t.getName());
                    m.put("type", t.getType() != null ? String.valueOf(t.getType()) : null);
                    m.put("channel", t.getChannel() != null ? String.valueOf(t.getChannel()) : null);
                    m.put("subject", t.getSubject());
                    m.put("isActive", Boolean.TRUE.equals(t.getIsActive()));
                    m.put("isMandatory", Boolean.TRUE.equals(t.getIsMandatory()));
                    return m;
                })
                .toList();
        return ResponseEntity.ok(out);
    }

    public record TemplateStatusRequest(Boolean isActive) {}

    @PatchMapping("/notification-templates/{id}/status")
    @PreAuthorize("hasPermission(null, 'PLATFORM', 'platform.notifications.manage')")
    public ResponseEntity<Map<String, Object>> setTemplateStatus(
            @PathVariable UUID id,
            @RequestBody TemplateStatusRequest body) {
        if (body == null || body.isActive() == null) {
            throw new BusinessException("'isActive' is required", "VALIDATION_ERROR");
        }
        NotificationTemplate t = templateRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Template not found", "TEMPLATE_NOT_FOUND"));
        if (Boolean.TRUE.equals(t.getIsMandatory())) {
            throw new BusinessException("Mandatory templates cannot be disabled", "TEMPLATE_MANDATORY");
        }
        t.setIsActive(body.isActive());
        templateRepository.save(t);
        return ResponseEntity.ok(Map.of("id", id, "isActive", body.isActive()));
    }

    // ---------------------------------------------------------------------
    // Broadcasts
    // ---------------------------------------------------------------------

    public record BroadcastRequest(String title, String message, List<String> channels,
                                   boolean targetAll, List<UUID> businessIds) {}

    @PostMapping("/broadcasts")
    @PreAuthorize("""
        hasPermission(null, 'PLATFORM', 'platform.notifications.manage')
        or hasPermission(null, 'PLATFORM', 'platform.settings.manage')
    """)
    public ResponseEntity<Map<String, Object>> broadcast(@RequestBody BroadcastRequest body) {
        if (body == null || body.title() == null || body.title().isBlank()
                || body.message() == null || body.message().isBlank()) {
            throw new BusinessException("'title' and 'message' are required", "VALIDATION_ERROR");
        }

        List<UUID> targets = body.targetAll()
                ? businessRepository.findAll(Sort.by("name")).stream()
                        .filter(b -> b.getStatus() != Business.Status.CANCELLED)
                        .map(Business::getId).toList()
                : (body.businessIds() != null ? body.businessIds() : List.of());
        if (targets.isEmpty()) {
            throw new BusinessException("No target businesses resolved", "NO_TARGETS");
        }

        List<String> channels = (body.channels() == null || body.channels().isEmpty())
                ? List.of("IN_APP") : body.channels();

        int queued = 0;
        for (UUID businessId : targets) {
            for (String channel : channels) {
                SendNotificationRequest req = new SendNotificationRequest();
                req.setType("SYSTEM");
                req.setChannel(channel.toUpperCase());
                req.setTitle(body.title());
                req.setBody(body.message());
                try {
                    var result = notificationSender.send(businessId, req);
                    queued += result.queued();
                } catch (Exception e) {
                    // One failing tenant/channel must not abort the broadcast
                }
            }
        }

        return ResponseEntity.ok(Map.of(
                "targetBusinesses", targets.size(),
                "channels", channels,
                "notificationsQueued", queued
        ));
    }
}
