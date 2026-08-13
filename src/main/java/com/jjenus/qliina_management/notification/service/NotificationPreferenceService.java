package com.jjenus.qliina_management.notification.service;

import com.jjenus.qliina_management.notification.dto.NotificationPreferenceDTO;
import com.jjenus.qliina_management.notification.model.Notification;
import com.jjenus.qliina_management.notification.model.UserNotificationPreference;
import com.jjenus.qliina_management.notification.repository.UserNotificationPreferenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NotificationPreferenceService {

    private final UserNotificationPreferenceRepository preferenceRepository;

    @Transactional(readOnly = true)
    public List<NotificationPreferenceDTO> getPreferences(UUID userId) {
        return preferenceRepository.findByUserId(userId)
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Transactional
    public NotificationPreferenceDTO upsertPreference(UUID userId, UUID businessId,
                                                       NotificationPreferenceDTO dto) {
        Notification.NotificationChannel channel =
                Notification.NotificationChannel.valueOf(dto.getChannel());
        Notification.NotificationType type =
                Notification.NotificationType.valueOf(dto.getNotificationType());

        UserNotificationPreference pref =
                preferenceRepository
                    .findByUserIdAndChannelAndNotificationType(userId, channel, type)
                    .orElse(new UserNotificationPreference());

        pref.setUserId(userId);
        pref.setBusinessId(businessId);
        pref.setChannel(channel);
        pref.setNotificationType(type);
        pref.setEnabled(dto.isEnabled());

        return toDTO(preferenceRepository.save(pref));
    }

    /** Returns true when the user is opted in (missing row = opted in by default). */
    public boolean isOptedIn(UUID userId, Notification.NotificationChannel channel,
                              Notification.NotificationType type) {
        return preferenceRepository
                .findByUserIdAndChannelAndNotificationType(userId, channel, type)
                .map(UserNotificationPreference::isEnabled)
                .orElse(true);
    }

    /**
     * Centralized preference check — every fan-out path (outbox worker, bulk
     * jobs, immediate in-app sends) MUST go through this instead of reading
     * preferences inline, so opt-out can never drift between senders (doc §3).
     * Opt-out system: a missing row defaults to enabled. Mandatory templates
     * bypass this entirely at the fan-out layer.
     */
    public boolean resolvePreference(UUID businessId, UUID userId,
                                     Notification.NotificationType type,
                                     Notification.NotificationChannel channel) {
        return isOptedIn(userId, channel, type);
    }

    private NotificationPreferenceDTO toDTO(UserNotificationPreference p) {
        return NotificationPreferenceDTO.builder()
                .id(p.getId())
                .channel(p.getChannel().name())
                .notificationType(p.getNotificationType().name())
                .enabled(p.isEnabled())
                .build();
    }
}
