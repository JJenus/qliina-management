package com.jjenus.qliina_management.notification.model;

import com.jjenus.qliina_management.common.BaseTenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Append-only event stream for a delivery (doc §2 delivery_events).
 * A single delivery accumulates events over its lifetime: sent, delivered,
 * opened, clicked, or a terminal failed.
 */
@Entity
@Table(name = "notification_delivery_events", indexes = {
        @Index(name = "idx_notif_delivery_event_delivery", columnList = "delivery_id"),
        @Index(name = "idx_notif_delivery_event_time", columnList = "occurred_at")
})
@Getter
@Setter
public class NotificationDeliveryEvent extends BaseTenantEntity {

    @Column(name = "delivery_id", nullable = false)
    private UUID deliveryId;

    @Column(name = "event_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private NotificationDeliveryEventType eventType;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "detail")
    private String detail;
}