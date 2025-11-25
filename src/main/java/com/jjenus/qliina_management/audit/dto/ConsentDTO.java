package com.jjenus.qliina_management.audit.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsentDTO {
    private UUID id;
    private UUID customerId;
    private String consentType;
    private Boolean granted;
    private LocalDateTime grantedAt;
    private String ipAddress;
    private String userAgent;
    private LocalDateTime revokedAt;
    private String consentVersion;
    private LocalDateTime expiresAt;
    private String status;
}
