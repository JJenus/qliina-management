package com.jjenus.qliina_management.notification.service.config;

import com.jjenus.qliina_management.common.BusinessException;
import com.jjenus.qliina_management.notification.dto.EmailConfigurationDTO;
import com.jjenus.qliina_management.notification.model.EmailConfiguration;
import com.jjenus.qliina_management.notification.repository.EmailConfigurationRepository;
import com.jjenus.qliina_management.common.security.EncryptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EmailConfigService {
    
    private final EmailConfigurationRepository emailConfigRepository;
    private final EncryptionService encryptionService;
    
    @Transactional(readOnly = true)
    public EmailConfigurationDTO getConfiguration(UUID businessId) {
        EmailConfiguration config = emailConfigRepository.findByBusinessId(businessId)
            .orElseThrow(() -> new BusinessException("Email configuration not found", "EMAIL_CONFIG_NOT_FOUND"));
        return mapToDTO(config);
    }
    
    @Transactional
    public EmailConfigurationDTO configureEmail(UUID businessId, EmailConfigurationDTO request) {
        EmailConfiguration config = emailConfigRepository.findByBusinessId(businessId)
            .orElse(new EmailConfiguration());
        
        config.setBusinessId(businessId);
        config.setHost(request.getHost());
        config.setPort(request.getPort());
        config.setUsername(request.getUsername());
        config.setPasswordEncrypted(encryptionService.encrypt(request.getPassword()));
        config.setFromAddress(request.getFromAddress());
        config.setFromName(request.getFromName());
        config.setUseTls(request.getUseTls());
        config.setUseSsl(request.getUseSsl());
        config.setIsConfigured(true);
        
        config = emailConfigRepository.save(config);
        return mapToDTO(config);
    }
    
    @Transactional
    public void testConfiguration(UUID businessId, String testEmail) {
        EmailConfiguration config = emailConfigRepository.findByBusinessId(businessId)
            .orElseThrow(() -> new BusinessException("Email not configured", "EMAIL_NOT_CONFIGURED"));
        
        // Test logic would go here - send test email, validate connection, etc.
        if (config.getHost() == null || config.getPort() == null) {
            throw new BusinessException("Invalid email configuration", "INVALID_EMAIL_CONFIG");
        }
    }
    
    private EmailConfigurationDTO mapToDTO(EmailConfiguration config) {
        return EmailConfigurationDTO.builder()
            .businessId(config.getBusinessId())
            .host(config.getHost())
            .port(config.getPort())
            .username(config.getUsername())
            .fromAddress(config.getFromAddress())
            .fromName(config.getFromName())
            .useTls(config.getUseTls())
            .useSsl(config.getUseSsl())
            .isConfigured(config.getIsConfigured())
            .build();
    }
}
