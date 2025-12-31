package com.jjenus.qliina_management.notification.dto;

import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class MarkReadRequest {
    private List<UUID> notificationIds;
    private Boolean markAll;
}
