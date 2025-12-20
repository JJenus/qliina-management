package com.jjenus.qliina_management.notification.model;

import com.jjenus.qliina_management.common.BaseTenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_devices", indexes = {
    @Index(name = "idx_device_user", columnList = "user_id"),
    @Index(name = "idx_device_token", columnList = "push_token"),
    @Index(name = "idx_device_active", columnList = "is_active")
})
@Getter
@Setter
public class UserDevice extends BaseTenantEntity {
    
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    
    @Column(name = "device_id")
    private String deviceId;
    
    @Column(name = "device_type")
    @Enumerated(EnumType.STRING)
    private DeviceType deviceType;
    
    @Column(name = "push_token")
    private String pushToken;
    
    @Column(name = "is_active")
    private Boolean isActive = true;
    
    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;
    
    @Column(name = "app_version")
    private String appVersion;
    
    @Column(name = "os_version")
    private String osVersion;
    
    @Column(name = "model")
    private String model;
    
    public enum DeviceType {
        ANDROID, IOS, WEB, TABLET
    }
}
