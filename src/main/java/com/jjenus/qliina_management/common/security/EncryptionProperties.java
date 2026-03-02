package com.jjenus.qliina_management.common.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "encryption")
public class EncryptionProperties {
    
    /**
     * Secret key for encryption (should be stored in environment variable or secret manager)
     */
    private String secret;
    
    /**
     * Salt for key derivation
     */
    private String salt;
    
    /**
     * Number of PBKDF2 iterations (higher = more secure but slower)
     */
    private int iterationCount = 65536;
    
    /**
     * Key length in bits
     */
    private int keyLength = 256;
    
    /**
     * Whether to enable encryption (can be disabled for testing)
     */
    private boolean enabled = false;
}