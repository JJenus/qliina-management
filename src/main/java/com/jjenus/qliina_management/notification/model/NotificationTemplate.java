package com.jjenus.qliina_management.notification.model;

import com.jjenus.qliina_management.common.BaseTenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Map;

@Entity
@Table(name = "notification_templates", indexes = {
    @Index(name = "idx_template_name", columnList = "name"),
    @Index(name = "idx_template_type", columnList = "type"),
    @Index(name = "idx_template_channel", columnList = "channel")
})
@Getter
@Setter
public class NotificationTemplate extends BaseTenantEntity {
    
    @Column(name = "name", nullable = false, unique = true)
    private String name;
    
    @Column(name = "description")
    private String description;
    
    @Column(name = "type", nullable = false)
    @Enumerated(EnumType.STRING)
    private Notification.NotificationType type;
    
    @Column(name = "channel", nullable = false)
    @Enumerated(EnumType.STRING)
    private Notification.NotificationChannel channel;
    
    @Column(name = "subject")
    private String subject; // For emails
    
    @Column(name = "title_template")
    private String titleTemplate;
    
    @Column(name = "body_template", nullable = false, length = 5000)
    private String bodyTemplate;
    
    @Column(name = "variables", columnDefinition = "text[]")
    private List<String> variables = new ArrayList<>();
    
    @Column(name = "is_active")
    private Boolean isActive = true;
    
    @Column(name = "business_id")
    private UUID businessId; // null for system templates
    
    public String renderTitle(Map<String, Object> data) {
        if (titleTemplate == null) return null;
        return renderTemplate(titleTemplate, data);
    }
    
    public String renderBody(Map<String, Object> data) {
        return renderTemplate(bodyTemplate, data);
    }
    
    private String renderTemplate(String template, Map<String, Object> data) {
        String result = template;
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String placeholder = "{{" + entry.getKey() + "}}";
            String value = entry.getValue() != null ? entry.getValue().toString() : "";
            result = result.replace(placeholder, value);
        }
        return result;
    }
}
