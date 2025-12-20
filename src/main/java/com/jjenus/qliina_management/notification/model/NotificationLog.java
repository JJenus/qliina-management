package com.jjenus.qliina_management.notification.model;

import com.jjenus.qliina_management.common.BaseTenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "notification_logs", indexes = {
    @Index(name = "idx_log_notification", columnList = "notification_id"),
    @Index(name = "idx_log_recipient", columnList = "recipient"),
    @Index(name = "idx_log_status", columnList = "status"),
    @Index(name = "idx_log_sent", columnList = "sent_at")
})
@Getter
@Setter
public class NotificationLog extends BaseTenantEntity {
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "notification_id")
    private Notification notification;
    
    @Column(name = "recipient", nullable = false)
    private String recipient;
    
    @Column(name = "channel", nullable = false)
    @Enumerated(EnumType.STRING)
    private Notification.NotificationChannel channel;
    
    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private DeliveryStatus status;
    
    @Column(name = "subject")
    private String subject;
    
    @Column(name = "content", length = 5000)
    private String content;
    
    @Column(name = "provider_response")
    private String providerResponse;
    
    @Column(name = "error_message")
    private String errorMessage;
    
    @Column(name = "sent_at")
    private LocalDateTime sentAt;
    
    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;
    
    @Column(name = "retry_count")
    private Integer retryCount = 0;
    
    public enum DeliveryStatus {
        PENDING, SENT, DELIVERED, FAILED, BOUNCED, COMPLAINT
    }
}
