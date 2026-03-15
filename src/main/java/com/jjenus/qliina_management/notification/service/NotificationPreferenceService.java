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

    private NotificationPreferenceDTO toDTO(UserNotificationPreference p) {
        return NotificationPreferenceDTO.builder()
                .id(p.getId())
                .channel(p.getChannel().name())
                .notificationType(p.getNotificationType().name())
                .enabled(p.isEnabled())
                .build();
    }
}
