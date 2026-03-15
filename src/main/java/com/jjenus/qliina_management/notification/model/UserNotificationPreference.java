package com.jjenus.qliina_management.notification.model;

import com.jjenus.qliina_management.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Per-user, per-channel, per-type notification opt-in/out preference.
 * Missing rows are treated as opted-in (opt-out model).
 */
@Entity
@Table(name = "user_notification_preferences",
       uniqueConstraints = @UniqueConstraint(
           name = "uq_notif_pref_user_channel_type",
           columnNames = {"user_id", "channel", "notification_type"}))
@Getter
@Setter
public class UserNotificationPreference extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "channel", nullable = false)
    @Enumerated(EnumType.STRING)
    private Notification.NotificationChannel channel;

    @Column(name = "notification_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private Notification.NotificationType notificationType;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;
}
