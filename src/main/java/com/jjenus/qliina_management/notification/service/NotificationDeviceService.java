package com.jjenus.qliina_management.notification.service;

import com.jjenus.qliina_management.common.BusinessException;
import com.jjenus.qliina_management.notification.dto.RegisterDeviceRequest;
import com.jjenus.qliina_management.notification.dto.UserDeviceDTO;
import com.jjenus.qliina_management.notification.model.UserDevice;
import com.jjenus.qliina_management.notification.repository.UserDeviceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NotificationDeviceService {
    
    private final UserDeviceRepository userDeviceRepository;
    
    @Transactional(readOnly = true)
    public List<UserDeviceDTO> getUserDevices(UUID userId) {
        return userDeviceRepository.findByUserIdAndIsActiveTrue(userId).stream()
            .map(this::convertToDTO)
            .collect(Collectors.toList());
    }
    
    @Transactional
    public UserDeviceDTO registerDevice(UUID businessId, UUID userId, RegisterDeviceRequest request) {
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
        return convertToDTO(device);
    }
    
    @Transactional
    public void unregisterDevice(UUID userId, String deviceId) {
        userDeviceRepository.findByUserIdAndDeviceId(userId, deviceId)
            .ifPresent(device -> {
                device.setIsActive(false);
                userDeviceRepository.save(device);
            });
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
}
