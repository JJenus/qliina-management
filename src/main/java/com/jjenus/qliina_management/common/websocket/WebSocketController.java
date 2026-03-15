package com.jjenus.qliina_management.common.websocket;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * WebSocket STOMP controller.
 *
 * ---- Client connection -----------------------------------------------
 * 1. Connect to  wss://{host}/ws  (SockJS fallback: http://{host}/ws)
 * 2. Pass STOMP header:  Authorization: Bearer {JWT}
 *
 * ---- Subscribe topics (server -> client) ----------------------------
 *   /topic/business.{businessId}.orders     -- ORDER_STATUS_CHANGED
 *   /topic/business.{businessId}.inventory  -- INVENTORY_LOW_STOCK
 *   /topic/business.{businessId}.dashboard  -- DASHBOARD_UPDATED
 *   /topic/business.{businessId}.quality    -- QUALITY_CHECK_UPDATED
 *   /queue/notifications                    -- IN_APP_NOTIFICATION (user-specific)
 *
 * ---- Publish topics (client -> server via /app prefix) --------------
 *   /app/notifications.ack  body: {notificationId}  -- mark notification read
 *
 * NOTE: Swagger does not natively document STOMP endpoints.
 * WebSocketDocsController below exposes REST stubs so Swagger shows the API.
 */
@Controller
@RequiredArgsConstructor
public class WebSocketController {

    private final WebSocketPublisher publisher;

    /**
     * Client sends to /app/notifications.ack with body {"notificationId": "..."}
     * to acknowledge a notification. Confirmation echoed to /queue/notifications.ack
     *
     * Persistence (mark-read) should also be done via the REST endpoint
     * POST /notifications/mark-read for durability.
     */
    @MessageMapping("/notifications.ack")
    @SendToUser("/notifications.ack")
    public Map<String, Object> acknowledgeNotification(
            Map<String, String> body,
            SimpMessageHeaderAccessor headerAccessor) {
        String notificationId = body.get("notificationId");
        return Map.of(
            "acked", true,
            "notificationId", notificationId != null ? notificationId : "unknown"
        );
    }
}


// ---------------------------------------------------------------------------
// Swagger documentation stubs -- REST endpoints that document the WS API
// ---------------------------------------------------------------------------

@Tag(name = "WebSocket -- Real-time Events",
     description = "WebSocket / STOMP real-time event API. "
         + "Connect to /ws using a STOMP client (SockJS fallback available). "
         + "Pass your JWT in the STOMP CONNECT frame header: Authorization: Bearer {token}. "
         + "All events are wrapped in a WebSocketEvent envelope with fields: "
         + "eventType, businessId, entityId, timestamp, payload. "
         + "Subscribe topics (server -> client): "
         + "/topic/business.{id}.orders (ORDER_STATUS_CHANGED), "
         + "/topic/business.{id}.inventory (INVENTORY_LOW_STOCK), "
         + "/topic/business.{id}.dashboard (DASHBOARD_UPDATED), "
         + "/topic/business.{id}.quality (QUALITY_CHECK_UPDATED), "
         + "/queue/notifications (IN_APP_NOTIFICATION, user-specific). "
         + "Publish topics (client -> server via /app prefix): "
         + "/app/notifications.ack body={notificationId} reply=/queue/notifications.ack")
@RestController
@RequestMapping("/api/v1/ws-docs")
class WebSocketDocsController {

    @Operation(
        summary = "WS: Order status updates",
        description = "Subscribe to /topic/business.{businessId}.orders for real-time order status changes. "
                     + "Payload: OrderStatusDTO wrapped in WebSocketEvent.",
        responses = @ApiResponse(responseCode = "501",
            description = "Documentation-only endpoint. Connect via STOMP WebSocket.",
            content = @Content(schema = @Schema(implementation = WebSocketEvent.class))))
    @GetMapping("/business/{businessId}/orders")
    public void docOrderUpdates(
            @Parameter(description = "Business ID") @PathVariable UUID businessId) {
        throw new UnsupportedOperationException("WebSocket endpoint -- use STOMP client");
    }

    @Operation(
        summary = "WS: Inventory low-stock alerts",
        description = "Subscribe to /topic/business.{businessId}.inventory for low-stock alerts.",
        responses = @ApiResponse(responseCode = "501",
            description = "Documentation-only endpoint.",
            content = @Content(schema = @Schema(implementation = WebSocketEvent.class))))
    @GetMapping("/business/{businessId}/inventory")
    public void docInventoryAlerts(
            @Parameter(description = "Business ID") @PathVariable UUID businessId) {
        throw new UnsupportedOperationException("WebSocket endpoint -- use STOMP client");
    }

    @Operation(
        summary = "WS: Dashboard KPI updates",
        description = "Subscribe to /topic/business.{businessId}.dashboard for live KPI updates.",
        responses = @ApiResponse(responseCode = "501",
            description = "Documentation-only endpoint.",
            content = @Content(schema = @Schema(implementation = WebSocketEvent.class))))
    @GetMapping("/business/{businessId}/dashboard")
    public void docDashboardUpdates(
            @Parameter(description = "Business ID") @PathVariable UUID businessId) {
        throw new UnsupportedOperationException("WebSocket endpoint -- use STOMP client");
    }

    @Operation(
        summary = "WS: Quality check updates",
        description = "Subscribe to /topic/business.{businessId}.quality for quality-check status changes.",
        responses = @ApiResponse(responseCode = "501",
            description = "Documentation-only endpoint.",
            content = @Content(schema = @Schema(implementation = WebSocketEvent.class))))
    @GetMapping("/business/{businessId}/quality")
    public void docQualityUpdates(
            @Parameter(description = "Business ID") @PathVariable UUID businessId) {
        throw new UnsupportedOperationException("WebSocket endpoint -- use STOMP client");
    }

    @Operation(
        summary = "WS: In-app notifications (user-specific)",
        description = "Subscribe to /queue/notifications for the authenticated user's in-app notifications.",
        responses = @ApiResponse(responseCode = "501",
            description = "Documentation-only endpoint.",
            content = @Content(schema = @Schema(implementation = WebSocketEvent.class))))
    @GetMapping("/user/notifications")
    public void docUserNotifications() {
        throw new UnsupportedOperationException("WebSocket endpoint -- use STOMP client");
    }
}
