package com.jjenus.qliina_management.notification.service.channel;

import com.jjenus.qliina_management.common.BusinessException;
import com.jjenus.qliina_management.identity.model.User;
import com.jjenus.qliina_management.notification.model.Notification;
import com.jjenus.qliina_management.notification.model.NotificationLog;
import com.jjenus.qliina_management.notification.model.SMSConfiguration;
import com.jjenus.qliina_management.notification.repository.NotificationLogRepository;
import com.jjenus.qliina_management.notification.repository.SMSConfigurationRepository;
import com.jjenus.qliina_management.common.security.EncryptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SmsChannelService {
    
    private final SMSConfigurationRepository smsConfigRepository;
    private final NotificationLogRepository logRepository;
    private final EncryptionService encryptionService;
    
    public void send(Notification notification, User user) {
        SMSConfiguration config = getConfig(notification.getBusinessId());
        
        try {
            switch (config.getProvider()) {
                case TWILIO:
                    sendViaTwilio(config, user.getPhone(), notification.getBody());
                    break;
                case AWS_SNS:
                    sendViaAWSSNS(config, user.getPhone(), notification.getBody());
                    break;
                case VONAGE:
                    sendViaVonage(config, user.getPhone(), notification.getBody());
                    break;
                default:
                    throw new BusinessException("Unsupported SMS provider", "UNSUPPORTED_PROVIDER");
            }
            
            createLog(notification, user.getPhone(), "SMS sent successfully");
            log.info("SMS sent to: {}", user.getPhone());
            
        } catch (Exception e) {
            createErrorLog(notification, user.getPhone(), e.getMessage());
            throw new BusinessException("Failed to send SMS: " + e.getMessage(), "SMS_SEND_FAILED");
        }
    }
    
    public void sendTest(UUID businessId, String recipient, String message) {
        SMSConfiguration config = getConfig(businessId);
        
        try {
            switch (config.getProvider()) {
                case TWILIO:
                    sendViaTwilio(config, recipient, message);
                    break;
                case AWS_SNS:
                    sendViaAWSSNS(config, recipient, message);
                    break;
                case VONAGE:
                    sendViaVonage(config, recipient, message);
                    break;
                default:
                    throw new BusinessException("Unsupported SMS provider", "UNSUPPORTED_PROVIDER");
            }
            log.info("Test SMS sent to: {}", recipient);
            
        } catch (Exception e) {
            throw new BusinessException("Failed to send test SMS: " + e.getMessage(), "TEST_SMS_FAILED");
        }
    }
    
    private void sendViaTwilio(SMSConfiguration config, String to, String message) {
        String accountSid = encryptionService.decrypt(config.getAccountSidEncrypted());
        String authToken = encryptionService.decrypt(config.getAuthTokenEncrypted());
        log.info("Sending Twilio SMS from {} to: {}", config.getFromNumber(), to);
        // Actual Twilio API call would be here
    }
    
    private void sendViaAWSSNS(SMSConfiguration config, String to, String message) {
        log.info("Sending AWS SNS SMS to: {}", to);
        // AWS SNS implementation would go here
    }
    
    private void sendViaVonage(SMSConfiguration config, String to, String message) {
        log.info("Sending Vonage SMS to: {}", to);
        // Vonage implementation would go here
    }
    
    private SMSConfiguration getConfig(UUID businessId) {
        return smsConfigRepository.findByBusinessId(businessId)
            .orElseThrow(() -> new BusinessException("SMS not configured for business", "SMS_NOT_CONFIGURED"));
    }
    
    private void createLog(Notification notification, String recipient, String message) {
        NotificationLog log = new NotificationLog();
        log.setBusinessId(notification.getBusinessId());
        log.setNotification(notification);
        log.setRecipient(recipient);
        log.setChannel(notification.getChannel());
        log.setStatus(NotificationLog.DeliveryStatus.SENT);
        log.setSubject("SMS Notification");
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
        log.setSubject("SMS Notification");
        log.setErrorMessage(error);
        log.setSentAt(LocalDateTime.now());
        
        logRepository.save(log);
    }
}
