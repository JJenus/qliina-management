package com.jjenus.qliina_management.audit.model;

import com.jjenus.qliina_management.common.BaseTenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "security_events", indexes = {
    @Index(name = "idx_security_timestamp", columnList = "timestamp"),
    @Index(name = "idx_security_user", columnList = "user_id"),
    @Index(name = "idx_security_type", columnList = "event_type"),
    @Index(name = "idx_security_ip", columnList = "ip_address")
})
@Getter
@Setter
public class SecurityEvent extends BaseTenantEntity {
    
    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;
    
    @Column(name = "event_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private SecurityEventType eventType;
    
    @Column(name = "severity")
    @Enumerated(EnumType.STRING)
    private AuditLog.AuditSeverity severity;
    
    @Column(name = "user_id")
    private UUID userId;
    
    @Column(name = "username")
    private String username;
    
    @Column(name = "business_id")
    private UUID businessId;
    
    @Column(name = "ip_address")
    private String ipAddress;
    
    @Column(name = "user_agent")
    private String userAgent;
    
    @Column(name = "location")
    private String location;
    
    @Column(name = "device_fingerprint")
    private String deviceFingerprint;
    
    @Column(name = "details", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String details;
    
    @Column(name = "blocked")
    private Boolean blocked = false;
    
    @Column(name = "block_reason")
    private String blockReason;
    
    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;
    
    @Column(name = "resolved_by")
    private UUID resolvedBy;
    
    public enum SecurityEventType {
        LOGIN_SUCCESS, LOGIN_FAILED, LOGOUT, PASSWORD_CHANGE, PASSWORD_RESET,
        TWO_FACTOR_ENABLED, TWO_FACTOR_DISABLED, TWO_FACTOR_FAILED,
        PERMISSION_CHANGE, ROLE_CHANGE, USER_LOCKED, USER_UNLOCKED,
        SUSPICIOUS_ACTIVITY, BRUTE_FORCE_ATTEMPT, API_ABUSE,
        DATA_EXPORT, DATA_DELETE, CONSENT_CHANGE
    }
}
