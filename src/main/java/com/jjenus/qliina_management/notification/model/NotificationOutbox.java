package com.jjenus.qliina_management.notification.model;

import com.jjenus.qliina_management.common.BaseTenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Transactional outbox row — the durable queue decoupling notification creation
 * and provider dispatch from the request path (doc §5).
 *
 * <p>A row is claimed by a worker under a lease (locked_at / locked_by). If the
 * worker crashes mid-processing the lease expires and the row is re-picked —
 * that is queue-level redelivery. Delivery-level retries (doc §4) are tracked
 * on {@link NotificationDelivery} and are deliberately a separate mechanism.
 */
@Entity
@Table(name = "notification_outbox", indexes = {
        @Index(name = "idx_notif_outbox_claim", columnList = "status, next_attempt_at"),
        @Index(name = "idx_notif_outbox_schedule", columnList = "scheduled_for"),
        @Index(name = "idx_notif_outbox_business", columnList = "business_id")
})
@Getter
@Setter
public class NotificationOutbox extends BaseTenantEntity {

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "template_id")
    private UUID templateId;

    @Column(name = "type", nullable = false)
    @Enumerated(EnumType.STRING)
    private Notification.NotificationType type;

    @Column(name = "channel")
    @Enumerated(EnumType.STRING)
    private Notification.NotificationChannel channel;

    @Column(name = "mandatory", nullable = false)
    private Boolean mandatory = false;

    @Column(name = "priority")
    @Enumerated(EnumType.STRING)
    private Notification.NotificationPriority priority = Notification.NotificationPriority.NORMAL;

    @Column(name = "title")
    private String title;

    @Column(name = "body")
    private String body;

    @Column(name = "data", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> data;

    @Column(name = "scheduled_for")
    private LocalDateTime scheduledFor;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private NotificationOutboxStatus status = NotificationOutboxStatus.PENDING;

    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount = 0;

    @Column(name = "max_attempts", nullable = false)
    private Integer maxAttempts = 10;

    @Column(name = "next_attempt_at")
    private LocalDateTime nextAttemptAt;

    @Column(name = "locked_at")
    private LocalDateTime lockedAt;

    @Column(name = "locked_by")
    private UUID lockedBy;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;
}