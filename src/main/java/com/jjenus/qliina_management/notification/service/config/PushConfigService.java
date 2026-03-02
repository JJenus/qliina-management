package com.jjenus.qliina_management.notification.service.config;

import com.jjenus.qliina_management.common.BusinessException;
import com.jjenus.qliina_management.notification.dto.PushConfigurationDTO;
import com.jjenus.qliina_management.notification.model.PushNotificationConfiguration;
import com.jjenus.qliina_management.notification.repository.PushNotificationConfigurationRepository;
import com.jjenus.qliina_management.common.security.EncryptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PushConfigService {
    
    private final PushNotificationConfigurationRepository pushConfigRepository;
    private final EncryptionService encryptionService;
    
    @Transactional(readOnly = true)
    public PushConfigurationDTO getConfiguration(UUID businessId) {
        PushNotificationConfiguration config = pushConfigRepository.findByBusinessId(businessId)
            .orElseThrow(() -> new BusinessException("Push configuration not found", "PUSH_CONFIG_NOT_FOUND"));
        return mapToDTO(config);
    }
    
    @Transactional
    public PushConfigurationDTO configurePush(UUID businessId, PushConfigurationDTO request) {
        PushNotificationConfiguration config = pushConfigRepository.findByBusinessId(businessId)
            .orElse(new PushNotificationConfiguration());
        
        config.setBusinessId(businessId);
        config.setIsConfigured(true);
        // Add actual push configuration logic here
        
        config = pushConfigRepository.save(config);
        return mapToDTO(config);
    }
    
    private PushConfigurationDTO mapToDTO(PushNotificationConfiguration config) {
        return PushConfigurationDTO.builder()
            .businessId(config.getBusinessId())
            .isConfigured(config.getIsConfigured())
            .build();
    }
}
