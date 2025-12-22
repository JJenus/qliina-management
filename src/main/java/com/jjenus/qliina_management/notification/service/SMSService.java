package com.jjenus.qliina_management.notification.service;

import com.jjenus.qliina_management.common.BusinessException;
import com.jjenus.qliina_management.notification.model.SMSConfiguration;
import com.jjenus.qliina_management.notification.repository.SMSConfigurationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.jjenus.qliina_management.notification.dto.SMSConfigurationDTO;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SMSService {
    
    private final SMSConfigurationRepository smsConfigRepository;
    private final EncryptionService encryptionService;
    
    public void sendSMS(UUID businessId, String to, String message) {
        SMSConfiguration config = smsConfigRepository.findByBusinessId(businessId)
            .orElseThrow(() -> new BusinessException("SMS not configured for business", "SMS_NOT_CONFIGURED"));
        
        try {
            switch (config.getProvider()) {
                case TWILIO:
                    sendViaTwilio(config, to, message);
                    break;
                case AWS_SNS:
                    sendViaAWSSNS(config, to, message);
                    break;
                case VONAGE:
                    sendViaVonage(config, to, message);
                    break;
                default:
                    throw new BusinessException("Unsupported SMS provider", "UNSUPPORTED_PROVIDER");
            }
            log.info("SMS sent successfully to: {}", to);
            
        } catch (Exception e) {
            log.error("Failed to send SMS to: {}", to, e);
            throw new BusinessException("Failed to send SMS: " + e.getMessage(), "SMS_SEND_FAILED");
        }
    }
    
    private void sendViaTwilio(SMSConfiguration config, String to, String message) {
        // Twilio implementation would go here
        String accountSid = encryptionService.decrypt(config.getAccountSidEncrypted());
        String authToken = encryptionService.decrypt(config.getAuthTokenEncrypted());
        
        log.info("Sending Twilio SMS from {} to: {}", config.getFromNumber(), to);
        // Actual Twilio API call would be here
    }
    
    private void sendViaAWSSNS(SMSConfiguration config, String to, String message) {
        // AWS SNS implementation would go here
        log.info("Sending AWS SNS SMS to: {}", to);
    }
    
    private void sendViaVonage(SMSConfiguration config, String to, String message) {
        // Vonage implementation would go here
        log.info("Sending Vonage SMS to: {}", to);
    }
    
    @Transactional
    public SMSConfiguration configureSMS(UUID businessId, SMSConfigurationDTO config) {
        SMSConfiguration smsConfig = smsConfigRepository.findByBusinessId(businessId)
            .orElse(new SMSConfiguration());
        
        smsConfig.setBusinessId(businessId);
        smsConfig.setProvider(SMSConfiguration.SMSProvider.valueOf(config.getProvider()));
        smsConfig.setAccountSidEncrypted(encryptionService.encrypt(config.getAccountSid()));
        smsConfig.setAuthTokenEncrypted(encryptionService.encrypt(config.getAuthToken()));
        smsConfig.setFromNumber(config.getFromNumber());
        smsConfig.setIsConfigured(true);
        
        return smsConfigRepository.save(smsConfig);
    }
}
