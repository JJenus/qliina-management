package com.jjenus.qliina_management.common.websocket;

import java.util.UUID;

/**
 * Centralised STOMP destination constants used across all modules.
 *
 * Usage (server side):
 *   messagingTemplate.convertAndSend(
 *       WebSocketTopics.orderUpdates(businessId), payload);
 *
 *   messagingTemplate.convertAndSendToUser(
 *       username,
 *       WebSocketTopics.USER_NOTIFICATIONS_SUFFIX,
 *       payload);
 */
public final class WebSocketTopics {

    private WebSocketTopics() {}

    public static String orderUpdates(UUID businessId) {
        return "/topic/business." + businessId + ".orders";
    }

    public static String inventoryAlerts(UUID businessId) {
        return "/topic/business." + businessId + ".inventory";
    }

    public static String dashboardUpdates(UUID businessId) {
        return "/topic/business." + businessId + ".dashboard";
    }

    public static String qualityUpdates(UUID businessId) {
        return "/topic/business." + businessId + ".quality";
    }

    public static String paymentUpdates(UUID businessId) {
        return "/topic/business." + businessId + ".payments";
    }

    /**
     * User destination constant for convertAndSendToUser(user, USER_NOTIFICATIONS_SUFFIX, payload).
     * The client subscribes to /user/queue/notifications.
     */
    public static final String USER_NOTIFICATIONS_SUFFIX = "/queue/notifications";
}
