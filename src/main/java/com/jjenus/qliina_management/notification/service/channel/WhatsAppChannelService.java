package com.jjenus.qliina_management.notification.service.channel;

import com.jjenus.qliina_management.common.BusinessException;
import com.jjenus.qliina_management.identity.model.User;
import com.jjenus.qliina_management.notification.model.Notification;
import com.jjenus.qliina_management.notification.model.NotificationLog;
import com.jjenus.qliina_management.notification.repository.NotificationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WhatsAppChannelService {
    
    private final NotificationLogRepository logRepository;
    
    public void send(Notification notification, User user) {
        try {
            // WhatsApp Business API implementation would go here
            log.info("Sending WhatsApp message to: {}", user.getPhone());
            
            createLog(notification, user.getPhone(), "WhatsApp message sent successfully");
            
        } catch (Exception e) {
            createErrorLog(notification, user.getPhone(), e.getMessage());
            throw new BusinessException("Failed to send WhatsApp message: " + e.getMessage(), "WHATSAPP_SEND_FAILED");
        }
    }
    
    public void sendTest(UUID businessId, String recipient, String message) {
        try {
            log.info("Sending test WhatsApp message to: {}", recipient);
            // WhatsApp test implementation
        } catch (Exception e) {
            throw new BusinessException("Failed to send test WhatsApp message: " + e.getMessage(), "TEST_WHATSAPP_FAILED");
        }
    }
    
    private void createLog(Notification notification, String recipient, String message) {
        NotificationLog log = new NotificationLog();
        log.setBusinessId(notification.getBusinessId());
        log.setNotification(notification);
        log.setRecipient(recipient);
        log.setChannel(notification.getChannel());
        log.setStatus(NotificationLog.DeliveryStatus.SENT);
        log.setSubject("WhatsApp Notification");
        log.setContent(notification.getBody());
        log.setSentAt(LocalDateTime.now());
        log.setProviderResponse(message);
        
        logRepository.save(log);
    }
    
    private void createErrorLog(Notification notification, String recipient, String error) {
        NotificationLog log = new NotificationLog();
        log.setBusinessId(notification.getBusinessId());
        log.setNotification(notification);
        log.setRecipient(recipient);
        log.setChannel(notification.getChannel());
        log.setStatus(NotificationLog.DeliveryStatus.FAILED);
        log.setSubject("WhatsApp Notification");
        log.setErrorMessage(error);
        log.setSentAt(LocalDateTime.now());
        
        logRepository.save(log);
    }
}
