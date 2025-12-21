package com.jjenus.qliina_management.notification.model;

import com.jjenus.qliina_management.common.BaseTenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "sms_configurations", indexes = {
    @Index(name = "idx_sms_business", columnList = "business_id", unique = true)
})
@Getter
@Setter
public class SMSConfiguration extends BaseTenantEntity {
    
    @Column(name = "business_id", nullable = false, unique = true)
    private UUID businessId;
    
    @Column(name = "provider")
    @Enumerated(EnumType.STRING)
    private SMSProvider provider;
    
    @Column(name = "account_sid_encrypted")
    private String accountSidEncrypted;
    
    @Column(name = "auth_token_encrypted")
    private String authTokenEncrypted;
    
    @Column(name = "from_number")
    private String fromNumber;
    
    @Column(name = "is_configured")
    private Boolean isConfigured = false;
    
    public enum SMSProvider {
        TWILIO, AWS_SNS, VONAGE, MESSAGEBIRD
    }
}
