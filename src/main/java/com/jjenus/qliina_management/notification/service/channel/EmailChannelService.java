package com.jjenus.qliina_management.notification.service.channel;

import com.jjenus.qliina_management.common.BusinessException;
import com.jjenus.qliina_management.identity.model.User;
import com.jjenus.qliina_management.notification.model.EmailConfiguration;
import com.jjenus.qliina_management.notification.model.Notification;
import com.jjenus.qliina_management.notification.model.NotificationLog;
import com.jjenus.qliina_management.notification.repository.EmailConfigurationRepository;
import com.jjenus.qliina_management.notification.repository.NotificationLogRepository;
import com.jjenus.qliina_management.common.security.EncryptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;
import java.time.LocalDateTime;
import java.util.Properties;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailChannelService {
    
    private final EmailConfigurationRepository emailConfigRepository;
    private final NotificationLogRepository logRepository;
    private final EncryptionService encryptionService;
    
    public void send(Notification notification, User user) {
        EmailConfiguration config = getConfig(notification.getBusinessId());
        JavaMailSender mailSender = createMailSender(config);
        
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            helper.setFrom(config.getFromAddress(), config.getFromName());
            helper.setTo(user.getEmail());
            helper.setSubject(notification.getTitle());
            helper.setText(notification.getBody(), true);
            
            mailSender.send(message);
            
            createLog(notification, user.getEmail(), "Email sent successfully");
            log.info("Email sent to: {}", user.getEmail());
            
        } catch (Exception e) {
            createErrorLog(notification, user.getEmail(), e.getMessage());
            throw new BusinessException("Failed to send email: " + e.getMessage(), "EMAIL_SEND_FAILED");
        }
    }
    
    public void sendTest(UUID businessId, String recipient, String subject, String content) {
        EmailConfiguration config = getConfig(businessId);
        JavaMailSender mailSender = createMailSender(config);
        
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            helper.setFrom(config.getFromAddress(), config.getFromName());
            helper.setTo(recipient);
            helper.setSubject(subject != null ? subject : "Test Notification");
            helper.setText(content != null ? content : "This is a test notification", true);
            
            mailSender.send(message);
            log.info("Test email sent to: {}", recipient);
            
        } catch (Exception e) {
            throw new BusinessException("Failed to send test email: " + e.getMessage(), "TEST_EMAIL_FAILED");
        }
    }
    
    private EmailConfiguration getConfig(UUID businessId) {
        return emailConfigRepository.findByBusinessId(businessId)
            .orElseThrow(() -> new BusinessException("Email not configured for business", "EMAIL_NOT_CONFIGURED"));
    }
    
    private JavaMailSender createMailSender(EmailConfiguration config) {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(config.getHost());
        mailSender.setPort(config.getPort());
        mailSender.setUsername(config.getUsername());
        mailSender.setPassword(encryptionService.decrypt(config.getPasswordEncrypted()));
        
        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", config.getUseTls());
        props.put("mail.smtp.ssl.enable", config.getUseSsl());
        
        return mailSender;
    }
    
    private void createLog(Notification notification, String recipient, String message) {
        NotificationLog log = new NotificationLog();
        log.setBusinessId(notification.getBusinessId());
        log.setNotification(notification);
        log.setRecipient(recipient);
        log.setChannel(notification.getChannel());
        log.setStatus(NotificationLog.DeliveryStatus.SENT);
        log.setSubject(notification.getTitle());
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
        log.setSubject(notification.getTitle());
        log.setErrorMessage(error);
        log.setSentAt(LocalDateTime.now());
        
        logRepository.save(log);
    }
}
