package com.jjenus.qliina_management.audit.model;

import com.jjenus.qliina_management.common.BaseTenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "consent_records", indexes = {
    @Index(name = "idx_consent_customer", columnList = "customer_id"),
    @Index(name = "idx_consent_type", columnList = "consent_type"),
    @Index(name = "idx_consent_status", columnList = "status")
})
@Getter
@Setter
public class ConsentRecord extends BaseTenantEntity {
    
    @Column(name = "customer_id", nullable = false)
    private UUID customerId;
    
    @Column(name = "consent_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private ConsentType consentType;
    
    @Column(name = "granted", nullable = false)
    private Boolean granted;
    
    @Column(name = "granted_at")
    private LocalDateTime grantedAt;
    
    @Column(name = "ip_address")
    private String ipAddress;
    
    @Column(name = "user_agent")
    private String userAgent;
    
    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;
    
    @Column(name = "revoked_ip")
    private String revokedIp;
    
    @Column(name = "revoked_by")
    private UUID revokedBy;
    
    @Column(name = "consent_version")
    private String consentVersion;
    
    @Column(name = "document_url")
    private String documentUrl;
    
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;
    
    @Column(name = "notes")
    private String notes;
    
    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private ConsentStatus status = ConsentStatus.ACTIVE;
    
    public enum ConsentType {
        MARKETING, SMS, EMAIL, DATA_PROCESSING, TERMS_AND_CONDITIONS, PRIVACY_POLICY, COOKIES
    }
    
    public enum ConsentStatus {
        ACTIVE, EXPIRED, REVOKED, SUPERSEDED
    }
    
    public void revoke(String ip, UUID revokedBy) {
        this.granted = false;
        this.revokedAt = LocalDateTime.now();
        this.revokedIp = ip;
        this.revokedBy = revokedBy;
        this.status = ConsentStatus.REVOKED;
    }
}
