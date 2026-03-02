package com.jjenus.qliina_management.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationStatsDTO {
    private Long totalSent;
    private Long totalDelivered;
    private Long totalFailed;
    private Double successRate;
    private Map<String, Long> byChannel;
    private Map<String, Long> byStatus;
}