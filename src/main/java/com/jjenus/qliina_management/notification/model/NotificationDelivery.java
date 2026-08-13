package com.jjenus.qliina_management.notification.model;

import com.jjenus.qliina_management.common.BaseTenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One channel attempt for a hub notification (doc §2 deliveries).
 *
 * <p>The notification/delivery split is the core of the design: the hub row is
 * "this user should be told X", while a delivery tracks ONE channel's attempt
 * with its own retry state. Retry state lives here — attempt_count,
 * max_attempts, next_retry_at, last_error_type — so the retry job queries this
 * table directly instead of scanning event history.
 */
@Entity
@Table(name = "notification_deliveries", indexes = {
        @Index(name = "idx_notif_delivery_notification", columnList = "notification_id"),
        @Index(name = "idx_notif_delivery_retry", columnList = "status, next_retry_at"),
        @Index(name = "idx_notif_delivery_business", columnList = "business_id")
})
@Getter
@Setter
public class NotificationDelivery extends BaseTenantEntity {

    @Column(name = "notification_id", nullable = false)
    private UUID notificationId;

    @Column(name = "channel", nullable = false)
    @Enumerated(EnumType.STRING)
    private Notification.NotificationChannel channel;

    @Column(name = "device_id")
    private UUID deviceId;

    @Column(name = "recipient")
    private String recipient;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private NotificationDeliveryStatus status = NotificationDeliveryStatus.PENDING;

    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount = 0;

    @Column(name = "max_attempts", nullable = false)
    private Integer maxAttempts = 5;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    @Column(name = "last_error_type")
    @Enumerated(EnumType.STRING)
    private NotificationDeliveryErrorType lastErrorType;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;
}