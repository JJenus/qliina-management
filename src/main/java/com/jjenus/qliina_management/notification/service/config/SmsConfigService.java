package com.jjenus.qliina_management.notification.service.config;

import com.jjenus.qliina_management.common.BusinessException;
import com.jjenus.qliina_management.notification.dto.SMSConfigurationDTO;
import com.jjenus.qliina_management.notification.model.SMSConfiguration;
import com.jjenus.qliina_management.notification.repository.SMSConfigurationRepository;
import com.jjenus.qliina_management.common.security.EncryptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SmsConfigService {
    
    private final SMSConfigurationRepository smsConfigRepository;
    private final EncryptionService encryptionService;
    
    @Transactional(readOnly = true)
    public SMSConfigurationDTO getConfiguration(UUID businessId) {
        SMSConfiguration config = smsConfigRepository.findByBusinessId(businessId)
            .orElseThrow(() -> new BusinessException("SMS configuration not found", "SMS_CONFIG_NOT_FOUND"));
        return mapToDTO(config);
    }
    
    @Transactional
    public SMSConfigurationDTO configureSms(UUID businessId, SMSConfigurationDTO request) {
        SMSConfiguration config = smsConfigRepository.findByBusinessId(businessId)
            .orElse(new SMSConfiguration());
        
        config.setBusinessId(businessId);
        config.setProvider(SMSConfiguration.SMSProvider.valueOf(request.getProvider()));
        config.setAccountSidEncrypted(encryptionService.encrypt(request.getAccountSid()));
        config.setAuthTokenEncrypted(encryptionService.encrypt(request.getAuthToken()));
        config.setFromNumber(request.getFromNumber());
        config.setIsConfigured(true);
        
        config = smsConfigRepository.save(config);
        return mapToDTO(config);
    }
    
    @Transactional
    public void testConfiguration(UUID businessId, String testPhoneNumber) {
        SMSConfiguration config = smsConfigRepository.findByBusinessId(businessId)
            .orElseThrow(() -> new BusinessException("SMS not configured", "SMS_NOT_CONFIGURED"));
        
        // Test logic would go here
        if (config.getProvider() == null) {
            throw new BusinessException("Invalid SMS configuration", "INVALID_SMS_CONFIG");
        }
    }
    
    private SMSConfigurationDTO mapToDTO(SMSConfiguration config) {
        return SMSConfigurationDTO.builder()
            .businessId(config.getBusinessId())
            .provider(config.getProvider() != null ? config.getProvider().name() : null)
            .fromNumber(config.getFromNumber())
            .isConfigured(config.getIsConfigured())
            .build();
    }
}
