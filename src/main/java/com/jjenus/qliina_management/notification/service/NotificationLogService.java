package com.jjenus.qliina_management.notification.service;

import com.jjenus.qliina_management.common.PageResponse;
import com.jjenus.qliina_management.notification.dto.NotificationLogDTO;
import com.jjenus.qliina_management.notification.dto.NotificationStatsDTO;
import com.jjenus.qliina_management.notification.model.Notification;
import com.jjenus.qliina_management.notification.model.NotificationLog;
import com.jjenus.qliina_management.notification.repository.NotificationLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NotificationLogService {
    
    private final NotificationLogRepository logRepository;
    
    @Transactional(readOnly = true)
    public PageResponse<NotificationLogDTO> getNotificationLogs(UUID businessId, String channel,
                                                                  String status, LocalDateTime fromDate,
                                                                  LocalDateTime toDate, Pageable pageable) {
        Notification.NotificationChannel ch = channel != null ?
            Notification.NotificationChannel.valueOf(channel) : null;
        NotificationLog.DeliveryStatus deliveryStatus = status != null ?
            NotificationLog.DeliveryStatus.valueOf(status) : null;
        
        Page<NotificationLog> page = logRepository.findByFilters(
            businessId, ch, deliveryStatus, fromDate, toDate, pageable);
        
        return PageResponse.from(page.map(this::mapToDTO));
    }
    
    @Transactional(readOnly = true)
    public NotificationStatsDTO getDeliveryStats(UUID businessId, LocalDateTime startDate, LocalDateTime endDate) {
        var logs = logRepository.findByBusinessIdAndDateRange(businessId, startDate, endDate);
        
        long totalSent = logs.size();
        long totalDelivered = logs.stream().filter(l -> l.getStatus() == NotificationLog.DeliveryStatus.DELIVERED).count();
        long totalFailed = logs.stream().filter(l -> l.getStatus() == NotificationLog.DeliveryStatus.FAILED).count();
        double successRate = totalSent > 0 ? (totalDelivered * 100.0 / totalSent) : 0;
        
        Map<String, Long> byChannel = logs.stream()
            .collect(Collectors.groupingBy(
                l -> l.getChannel().toString(),
                Collectors.counting()
            ));
        
        Map<String, Long> byStatus = logs.stream()
            .collect(Collectors.groupingBy(
                l -> l.getStatus().toString(),
                Collectors.counting()
            ));
        
        return NotificationStatsDTO.builder()
            .totalSent(totalSent)
            .totalDelivered(totalDelivered)
            .totalFailed(totalFailed)
            .successRate(successRate)
            .byChannel(byChannel)
            .byStatus(byStatus)
            .build();
    }
    
    private NotificationLogDTO mapToDTO(NotificationLog log) {
        return NotificationLogDTO.builder()
            .id(log.getId())
            .notificationId(log.getNotification() != null ? log.getNotification().getId() : null)
            .notificationType(log.getNotification() != null ? 
                log.getNotification().getType().toString() : null)
            .recipient(log.getRecipient())
            .channel(log.getChannel().toString())
            .status(log.getStatus().toString())
            .subject(log.getSubject())
            .errorMessage(log.getErrorMessage())
            .sentAt(log.getSentAt())
            .deliveredAt(log.getDeliveredAt())
            .retryCount(log.getRetryCount())
            .build();
    }
}
