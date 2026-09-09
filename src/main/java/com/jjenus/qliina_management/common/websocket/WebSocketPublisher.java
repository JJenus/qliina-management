package com.jjenus.qliina_management.common.websocket;

import com.jjenus.qliina_management.identity.model.User;
import com.jjenus.qliina_management.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Central service for publishing WebSocket events.
 * All publish calls are @Async so they never block the calling thread.
 *
 * Module services inject this and call the appropriate method after
 * persisting their domain changes.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebSocketPublisher {

    private final SimpMessagingTemplate messagingTemplate;
    private final UserRepository         userRepository;

    // ----------------------------------------------------------------
    // Order module -- broadcast to all sessions in a business
    // ----------------------------------------------------------------

    @Async
    public void publishOrderUpdate(UUID businessId, UUID orderId, Object payload) {
        send(WebSocketTopics.orderUpdates(businessId),
             buildEvent("ORDER_STATUS_CHANGED", businessId, orderId, payload));
    }

    // ----------------------------------------------------------------
    // Inventory module
    // ----------------------------------------------------------------

    @Async
    public void publishInventoryAlert(UUID businessId, UUID itemId, Object payload) {
        send(WebSocketTopics.inventoryAlerts(businessId),
             buildEvent("INVENTORY_LOW_STOCK", businessId, itemId, payload));
    }

    // ----------------------------------------------------------------
    // Dashboard / KPI module
    // ----------------------------------------------------------------

    @Async
    public void publishDashboardUpdate(UUID businessId, Object payload) {
        send(WebSocketTopics.dashboardUpdates(businessId),
             buildEvent("DASHBOARD_UPDATED", businessId, null, payload));
    }

    // ----------------------------------------------------------------
    // Quality-check module
    // ----------------------------------------------------------------

    @Async
    public void publishQualityUpdate(UUID businessId, UUID checkId, Object payload) {
        send(WebSocketTopics.qualityUpdates(businessId),
             buildEvent("QUALITY_CHECK_UPDATED", businessId, checkId, payload));
    }

    // ----------------------------------------------------------------
    // Payment module - broadcast to all sessions in a business
    // ----------------------------------------------------------------

    @Async
    public void publishPaymentUpdate(UUID businessId, UUID orderId, Object payload) {
        send(WebSocketTopics.paymentUpdates(businessId),
             buildEvent("PAYMENT_UPDATED", businessId, orderId, payload));
    }

    // ----------------------------------------------------------------
    // IN_APP notification -- per-user private queue
    // ----------------------------------------------------------------

    /**
     * Sends an in-app notification to one user's private STOMP queue.
     * The client subscribes to /user/queue/notifications.
     *
     * @param userId  target user UUID
     * @param payload notification DTO
     */
    @Async
    public void publishUserNotification(UUID userId, Object payload) {
        // STOMP user destinations resolve against the principal NAME, which for
        // this app is the username, not the userId UUID.
        String username = userRepository.findById(userId)
                .map(User::getUsername)
                .orElse(null);
        if (username == null) {
            log.warn("Cannot push in-app notification to unknown user {}; skipped", userId);
            return;
        }
        try {
            messagingTemplate.convertAndSendToUser(
                    username,
                    WebSocketTopics.USER_NOTIFICATIONS_SUFFIX,
                    payload);
            log.debug("Pushed in-app notification to user {} ({})", username, userId);
        } catch (Exception e) {
            log.error("Failed to push in-app notification to user {}: {}", username, e.getMessage());
        }
    }

    // ----------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------

    private <T> WebSocketEvent<T> buildEvent(String type, UUID businessId,
                                              UUID entityId, T payload) {
        return WebSocketEvent.<T>builder()
                .eventType(type)
                .businessId(businessId)
                .entityId(entityId)
                .payload(payload)
                .build();
    }

    private void send(String destination, Object event) {
        try {
            messagingTemplate.convertAndSend(destination, event);
            log.debug("WS event sent to {}", destination);
        } catch (Exception e) {
            log.error("Failed to send WS event to {}: {}", destination, e.getMessage());
        }
    }
}
