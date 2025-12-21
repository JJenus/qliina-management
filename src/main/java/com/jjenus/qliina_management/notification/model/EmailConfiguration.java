package com.jjenus.qliina_management.notification.model;

import com.jjenus.qliina_management.common.BaseTenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.UUID;

@Entity
@Table(name = "email_configurations", indexes = {
    @Index(name = "idx_email_business", columnList = "business_id", unique = true)
})
@Getter
@Setter
public class EmailConfiguration extends BaseTenantEntity {
    
    @Column(name = "business_id", nullable = false, unique = true)
    private UUID businessId;
    
    @Column(name = "host")
    private String host;
    
    @Column(name = "port")
    private Integer port;
    
    @Column(name = "username")
    private String username;
    
    @Column(name = "password_encrypted")
    private String passwordEncrypted;
    
    @Column(name = "from_address")
    private String fromAddress;
    
    @Column(name = "from_name")
    private String fromName;
    
    @Column(name = "use_tls")
    private Boolean useTls = true;
    
    @Column(name = "use_ssl")
    private Boolean useSsl = false;
    
    @Column(name = "is_configured")
    private Boolean isConfigured = false;
}
