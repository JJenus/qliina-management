package com.jjenus.qliina_management.notification.service;

import com.jjenus.qliina_management.common.BusinessException;
import com.jjenus.qliina_management.notification.model.PushNotificationConfiguration;
import com.jjenus.qliina_management.notification.model.UserDevice;
import com.jjenus.qliina_management.notification.dto.UserDeviceDTO;
import com.jjenus.qliina_management.notification.repository.PushNotificationConfigurationRepository;
import com.jjenus.qliina_management.notification.repository.UserDeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.jjenus.qliina_management.notification.dto.RegisterDeviceRequest;
import java.time.LocalDateTime;
import java.util.stream.Collectors;
 
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PushNotificationService {
    
    private final PushNotificationConfigurationRepository pushConfigRepository;
    private final UserDeviceRepository userDeviceRepository;
    private final EncryptionService encryptionService;
    
    public void sendPushNotification(UUID businessId, UUID userId, String title, String body, Object data) {
        PushNotificationConfiguration config = pushConfigRepository.findByBusinessId(businessId)
            .orElseThrow(() -> new BusinessException("Push notifications not configured", "PUSH_NOT_CONFIGURED"));
        
        List<UserDevice> devices = userDeviceRepository.findByUserIdAndIsActiveTrue(userId);
        
        for (UserDevice device : devices) {
            try {
                switch (device.getDeviceType()) {
                    case ANDROID:
                        sendToAndroid(config, device, title, body, data);
                        break;
                    case IOS:
                        sendToIOS(config, device, title, body, data);
                        break;
                    case WEB:
                        sendToWeb(config, device, title, body, data);
                        break;
                }
                log.info("Push notification sent to device: {}", device.getDeviceId());
                
            } catch (Exception e) {
                log.error("Failed to send push to device: {}", device.getDeviceId(), e);
            }
        }
    }
    
    private void sendToAndroid(PushNotificationConfiguration config, UserDevice device, 
                                String title, String body, Object data) {
        // FCM implementation would go here
        String serverKey = encryptionService.decrypt(config.getFcmServerKeyEncrypted());
        log.info("Sending Android push via FCM to device: {}", device.getPushToken());
    }
    
    private void sendToIOS(PushNotificationConfiguration config, UserDevice device, 
                            String title, String body, Object data) {
        // APNS implementation would go here
        log.info("Sending iOS push via APNS to device: {}", device.getPushToken());
    }
    
    private void sendToWeb(PushNotificationConfiguration config, UserDevice device, 
                            String title, String body, Object data) {
        // Web push implementation would go here
        log.info("Sending web push to device: {}", device.getPushToken());
    }
    
    @Transactional(readOnly = true)
public List<UserDeviceDTO> getUserDevices(UUID userId) {
    return userDeviceRepository.findByUserIdAndIsActiveTrue(userId).stream()
        .map(this::convertToDTO)
        .collect(Collectors.toList());
}
    
    @Transactional
public UserDeviceDTO registerDevice(UUID businessId, UUID userId, RegisterDeviceRequest request) {
    // Deactivate existing device if any
    userDeviceRepository.findByUserIdAndDeviceId(userId, request.getDeviceId())
        .ifPresent(device -> {
            device.setIsActive(false);
            userDeviceRepository.save(device);
        });
    
    UserDevice device = new UserDevice();
    device.setBusinessId(businessId);
    device.setUserId(userId);
    device.setDeviceId(request.getDeviceId());
    device.setDeviceType(UserDevice.DeviceType.valueOf(request.getDeviceType()));
    device.setPushToken(request.getPushToken());
    device.setAppVersion(request.getAppVersion());
    device.setOsVersion(request.getOsVersion());
    device.setModel(request.getModel());
    device.setIsActive(true);
    device.setLastUsedAt(LocalDateTime.now());
    
    device = userDeviceRepository.save(device);
    
    // Return DTO instead of entity
    return convertToDTO(device);
}

private UserDeviceDTO convertToDTO(UserDevice device) {
    if (device == null) return null;
    return UserDeviceDTO.builder()
        .id(device.getId())
        .userId(device.getUserId())
        .deviceId(device.getDeviceId())
        .deviceType(device.getDeviceType() != null ? device.getDeviceType().name() : null)
        .pushToken(device.getPushToken())
        .isActive(device.getIsActive())
        .lastUsedAt(device.getLastUsedAt())
        .appVersion(device.getAppVersion())
        .osVersion(device.getOsVersion())
        .model(device.getModel())
        .build();
}
    
    @Transactional
    public void unregisterDevice(UUID userId, String deviceId) {
        userDeviceRepository.findByUserIdAndDeviceId(userId, deviceId)
            .ifPresent(device -> {
                device.setIsActive(false);
                userDeviceRepository.save(device);
            });
    }
}
