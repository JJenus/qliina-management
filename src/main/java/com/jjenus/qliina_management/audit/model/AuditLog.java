package com.jjenus.qliina_management.audit.model;

import com.jjenus.qliina_management.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "audit_logs", indexes = {
    @Index(name = "idx_audit_timestamp", columnList = "timestamp"),
    @Index(name = "idx_audit_user", columnList = "user_id"),
    @Index(name = "idx_audit_business", columnList = "business_id"),
    @Index(name = "idx_audit_entity", columnList = "entity_type,entity_id"),
    @Index(name = "idx_audit_action", columnList = "action"),
    @Index(name = "idx_audit_ip", columnList = "ip_address")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog extends BaseEntity {
    
    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;
    
    @Column(name = "user_id")
    private UUID userId;
    
    @Column(name = "user_name")
    private String userName;
    
    @Column(name = "user_email")
    private String userEmail;
    
    @Column(name = "business_id")
    private UUID businessId;
    
    @Column(name = "shop_id")
    private UUID shopId;
    
    @Column(name = "entity_type", nullable = false)
    private String entityType;
    
    @Column(name = "entity_id")
    private UUID entityId;
    
    @Column(name = "entity_display")
    private String entityDisplay;
    
    @Column(name = "action", nullable = false)
    private String action;
    
    @Column(name = "category")
    private String category;
    
    @Column(name = "severity")
    @Enumerated(EnumType.STRING)
    private AuditSeverity severity;
    
    @Column(name = "old_value", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String oldValue;
    
    @Column(name = "new_value", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String newValue;
    
    @Column(name = "changes", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, ChangeDetail> changes;
    
    @Column(name = "ip_address")
    private String ipAddress;
    
    @Column(name = "user_agent")
    private String userAgent;
    
    @Column(name = "device_id")
    private String deviceId;
    
    @Column(name = "session_id")
    private String sessionId;
    
    @Column(name = "request_id")
    private String requestId;
    
    @Column(name = "request_path")
    private String requestPath;
    
    @Column(name = "request_method")
    private String requestMethod;
    
    @Column(name = "response_status")
    private Integer responseStatus;
    
    @Column(name = "execution_time_ms")
    private Long executionTimeMs;
    
    @Column(name = "details", columnDefinition = "text")
    private String details;
    
    @Column(name = "retention_until")
    private LocalDateTime retentionUntil;
    
    public enum AuditSeverity {
        INFO, WARNING, ERROR, CRITICAL
    }
    
    // This is just a POJO now, not an embeddable entity
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChangeDetail {
        private String field;
        private Object oldValue;
        private Object newValue;
        private String changeType; // MODIFIED, ADDED, REMOVED
    }
} 