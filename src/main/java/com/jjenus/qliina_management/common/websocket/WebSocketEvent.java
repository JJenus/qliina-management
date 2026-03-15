package com.jjenus.qliina_management.common.websocket;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Generic WebSocket message envelope sent over all STOMP topics.
 * Clients switch on eventType to determine how to render the payload.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Generic WebSocket event envelope")
public class WebSocketEvent<T> {

    @Schema(description = "Event type discriminator", example = "ORDER_STATUS_CHANGED")
    private String eventType;

    @Schema(description = "Business context for the event")
    private UUID businessId;

    @Schema(description = "Entity ID this event relates to (order, item, etc.)")
    private UUID entityId;

    @Builder.Default
    @Schema(description = "ISO-8601 timestamp the event was generated")
    private LocalDateTime timestamp = LocalDateTime.now();

    @Schema(description = "Event-specific payload")
    private T payload;
}
