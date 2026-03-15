package com.jjenus.qliina_management.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Notification preference for a user on a specific channel + type")
public class NotificationPreferenceDTO {

    @Schema(description = "Preference record ID (null when creating)")
    private UUID id;

    @NotNull
    @Schema(description = "Notification channel", example = "EMAIL",
            allowableValues = {"IN_APP","EMAIL","SMS","WHATSAPP","PUSH"})
    private String channel;

    @NotNull
    @Schema(description = "Notification type", example = "ORDER_STATUS",
            allowableValues = {"ORDER_STATUS","PAYMENT","REMINDER","PROMOTION","ALERT","SYSTEM"})
    private String notificationType;

    @Schema(description = "true = opted in, false = opted out")
    private boolean enabled;
}
