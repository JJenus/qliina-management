package com.jjenus.qliina_management.notification.repository;

import com.jjenus.qliina_management.notification.model.Notification;
import com.jjenus.qliina_management.notification.model.UserNotificationPreference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserNotificationPreferenceRepository
        extends JpaRepository<UserNotificationPreference, UUID> {

    List<UserNotificationPreference> findByUserId(UUID userId);

    Optional<UserNotificationPreference> findByUserIdAndChannelAndNotificationType(
            UUID userId,
            Notification.NotificationChannel channel,
            Notification.NotificationType notificationType);

    @Query("SELECT p FROM UserNotificationPreference p "
         + "WHERE p.userId = :userId AND p.enabled = false")
    List<UserNotificationPreference> findOptOuts(@Param("userId") UUID userId);
}
