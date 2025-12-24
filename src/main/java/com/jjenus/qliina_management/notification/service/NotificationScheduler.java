package com.jjenus.qliina_management.notification.service;

import com.jjenus.qliina_management.notification.model.Notification;
import com.jjenus.qliina_management.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationScheduler {
    
    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;
    
    @Scheduled(fixedDelay = 60000) // Run every minute
    @Transactional
    public void processScheduledNotifications() {
        log.debug("Processing scheduled notifications");
        
        List<Notification> dueNotifications = notificationRepository.findDueNotifications(LocalDateTime.now());
        
        for (Notification notification : dueNotifications) {
            try {
                notificationService.sendNotification(notification.getId());
            } catch (Exception e) {
                log.error("Failed to process scheduled notification: {}", notification.getId(), e);
            }
        }
    }
    
    @Scheduled(cron = "0 0 2 * * *") // Run at 2 AM daily
    @Transactional
    public void cleanupOldNotifications() {
        log.info("Cleaning up old notifications");
        
        LocalDateTime cutoffDate = LocalDateTime.now().minusMonths(3);
        // Delete or archive old notifications
    }
    
    @Scheduled(cron = "0 0 8 * * *") // Run at 8 AM daily
    @Transactional
    public void sendDailyReminders() {
        log.info("Sending daily reminders");
        // Implementation for daily reminders (unclaimed orders, etc.)
    }
}
