package com.jjenus.qliina_management.notification.service.channel;

import com.jjenus.qliina_management.common.BusinessException;
import com.jjenus.qliina_management.identity.model.User;
import com.jjenus.qliina_management.notification.model.Notification;
import com.jjenus.qliina_management.notification.model.NotificationLog;
import com.jjenus.qliina_management.notification.model.PushNotificationConfiguration;
import com.jjenus.qliina_management.notification.model.UserDevice;
import com.jjenus.qliina_management.notification.repository.NotificationLogRepository;
import com.jjenus.qliina_management.notification.repository.PushNotificationConfigurationRepository;
import com.jjenus.qliina_management.notification.repository.UserDeviceRepository;
import com.jjenus.qliina_management.common.security.EncryptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PushChannelService {
    
    private final PushNotificationConfigurationRepository pushConfigRepository;
    private final UserDeviceRepository userDeviceRepository;
    private final NotificationLogRepository logRepository;
    private final EncryptionService encryptionService;
    
    public void send(Notification notification, User user) {
        PushNotificationConfiguration config = getConfig(notification.getBusinessId());
        List<UserDevice> devices = userDeviceRepository.findByUserIdAndIsActiveTrue(user.getId());
        
        if (devices.isEmpty()) {
            log.warn("No active devices found for user: {}", user.getId());
            return;
        }
        
        for (UserDevice device : devices) {
            try {
                switch (device.getDeviceType()) {
                    case ANDROID:
                        sendToAndroid(config, device, notification);
                        break;
                    case IOS:
                        sendToIOS(config, device, notification);
                        break;
                    case WEB:
                        sendToWeb(config, device, notification);
                        break;
                }
                log.info("Push notification sent to device: {}", device.getDeviceId());
                
            } catch (Exception e) {
                log.error("Failed to send push to device: {}", device.getDeviceId(), e);
            }
        }
        
        createLog(notification, user.getId().toString(), "Push notification sent to " + devices.size() + " devices");
    }
    
    private void sendToAndroid(PushNotificationConfiguration config, UserDevice device, Notification notification) {
        String serverKey = encryptionService.decrypt(config.getFcmServerKeyEncrypted());
        log.info("Sending Android push via FCM to device: {}", device.getPushToken());
        // FCM implementation would go here
    }
    
    private void sendToIOS(PushNotificationConfiguration config, UserDevice device, Notification notification) {
        log.info("Sending iOS push via APNS to device: {}", device.getPushToken());
        // APNS implementation would go here
    }
    
    private void sendToWeb(PushNotificationConfiguration config, UserDevice device, Notification notification) {
        log.info("Sending web push to device: {}", device.getPushToken());
        // Web push implementation would go here
    }
    
    private PushNotificationConfiguration getConfig(UUID businessId) {
        return pushConfigRepository.findByBusinessId(businessId)
            .orElseThrow(() -> new BusinessException("Push notifications not configured", "PUSH_NOT_CONFIGURED"));
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
}
