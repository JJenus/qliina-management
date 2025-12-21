package com.jjenus.qliina_management.notification.model;

import com.jjenus.qliina_management.common.BaseTenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "push_configurations", indexes = {
    @Index(name = "idx_push_business", columnList = "business_id", unique = true)
})
@Getter
@Setter
public class PushNotificationConfiguration extends BaseTenantEntity {
    
    @Column(name = "business_id", nullable = false, unique = true)
    private UUID businessId;
    
    @Column(name = "fcm_server_key_encrypted")
    private String fcmServerKeyEncrypted;
    
    @Column(name = "apns_key_id")
    private String apnsKeyId;
    
    @Column(name = "apns_team_id")
    private String apnsTeamId;
    
    @Column(name = "apns_bundle_id")
    private String apnsBundleId;
    
    @Column(name = "apns_private_key_encrypted")
    private String apnsPrivateKeyEncrypted;
    
    @Column(name = "is_configured")
    private Boolean isConfigured = false;
}
