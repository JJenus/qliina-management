package com.jjenus.qliina_management.notification.service;

import com.jjenus.qliina_management.common.BusinessException;
import com.jjenus.qliina_management.notification.model.EmailConfiguration;
import com.jjenus.qliina_management.notification.repository.EmailConfigurationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;
import java.util.UUID;
import com.jjenus.qliina_management.notification.dto.EmailConfigurationDTO;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {
    
    private final EmailConfigurationRepository emailConfigRepository;
    private final EncryptionService encryptionService;
    
    public void sendEmail(UUID businessId, String to, String subject, String htmlContent) {
        EmailConfiguration config = emailConfigRepository.findByBusinessId(businessId)
            .orElseThrow(() -> new BusinessException("Email not configured for business", "EMAIL_NOT_CONFIGURED"));
        
        JavaMailSender mailSender = createMailSender(config);
        
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            helper.setFrom(config.getFromAddress(), config.getFromName());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            
            mailSender.send(message);
            log.info("Email sent successfully to: {}", to);
            
        } catch (Exception e) {
            log.error("Failed to send email to: {}", to, e);
            throw new BusinessException("Failed to send email: " + e.getMessage(), "EMAIL_SEND_FAILED");
        }
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
        props.put("mail.debug", "false");
        
        return mailSender;
    }
    
    @Transactional
    public EmailConfiguration configureEmail(UUID businessId, EmailConfigurationDTO config) {
        EmailConfiguration emailConfig = emailConfigRepository.findByBusinessId(businessId)
            .orElse(new EmailConfiguration());
        
        emailConfig.setBusinessId(businessId);
        emailConfig.setHost(config.getHost());
        emailConfig.setPort(config.getPort());
        emailConfig.setUsername(config.getUsername());
        emailConfig.setPasswordEncrypted(encryptionService.encrypt(config.getPassword()));
        emailConfig.setFromAddress(config.getFromAddress());
        emailConfig.setFromName(config.getFromName());
        emailConfig.setUseTls(config.getUseTls());
        emailConfig.setUseSsl(config.getUseSsl());
        emailConfig.setIsConfigured(true);
        
        return emailConfigRepository.save(emailConfig);
    }
}
