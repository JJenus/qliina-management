package com.jjenus.qliina_management.audit.service;

import com.jjenus.qliina_management.audit.dto.SecurityEventDTO;
import com.jjenus.qliina_management.audit.model.AuditLog;
import com.jjenus.qliina_management.audit.model.SecurityEvent;
import com.jjenus.qliina_management.audit.repository.SecurityEventRepository;
import com.jjenus.qliina_management.common.BusinessException;
import com.jjenus.qliina_management.common.PageResponse;
import com.jjenus.qliina_management.identity.model.User;
import com.jjenus.qliina_management.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SecurityEventService {
    
    private final SecurityEventRepository securityEventRepository;
    private final UserRepository userRepository;
    
    @Transactional
    public void logSecurityEvent(SecurityEvent.SecurityEventType eventType, String username, 
                                   String details, AuditLog.AuditSeverity severity) {
        HttpServletRequest request = getCurrentHttpRequest();
        User user = username != null ? userRepository.findByUsername(username).orElse(null) : null;
        
        SecurityEvent event = new SecurityEvent();
        event.setTimestamp(LocalDateTime.now());
        event.setEventType(eventType);
        event.setSeverity(severity);
        event.setUserId(user != null ? user.getId() : null);
        event.setUsername(username);
        event.setBusinessId(user != null ? user.getBusinessId() : null);
        event.setIpAddress(request != null ? request.getRemoteAddr() : null);
        event.setUserAgent(request != null ? request.getHeader("User-Agent") : null);
        event.setDetails(details);
        event.setBlocked(false);
        
        securityEventRepository.save(event);
        
        // Check for suspicious patterns
        checkForSuspiciousActivity(event);
    }
    
    @Transactional(readOnly = true)
    public PageResponse<SecurityEventDTO> getSecurityEvents(UUID businessId, UUID userId, String eventType,
                                                              String severity, LocalDateTime fromDate,
                                                              LocalDateTime toDate, String ipAddress,
                                                              Pageable pageable) {
        SecurityEvent.SecurityEventType type = eventType != null ?
            SecurityEvent.SecurityEventType.valueOf(eventType) : null;
        AuditLog.AuditSeverity sev = severity != null ?
            AuditLog.AuditSeverity.valueOf(severity) : null;
        
        Page<SecurityEvent> page = securityEventRepository.searchEvents(
            businessId, userId, type, sev, fromDate, toDate, ipAddress, pageable);
        
        return PageResponse.from(page.map(this::mapToDTO));
    }
    
    @Transactional
    public void blockIP(String ipAddress, String reason) {
        List<SecurityEvent> events = securityEventRepository.findFailedLoginsFromIp(
            ipAddress, LocalDateTime.now().minusHours(24));
        
        for (SecurityEvent event : events) {
            event.setBlocked(true);
            event.setBlockReason(reason);
            securityEventRepository.save(event);
        }
        
        log.warn("Blocked IP address: {} - Reason: {}", ipAddress, reason);
    }
    
    @Transactional
    public void resolveBlock(UUID eventId) {
        SecurityEvent event = securityEventRepository.findById(eventId)
            .orElseThrow(() -> new BusinessException("Event not found", "EVENT_NOT_FOUND"));
        
        event.setResolvedAt(LocalDateTime.now());
        event.setResolvedBy(getCurrentUserId());
        securityEventRepository.save(event);
    }
    
    @Scheduled(fixedDelay = 300000) // Run every 5 minutes
    @Transactional
    public void detectBruteForceAttacks() {
        LocalDateTime since = LocalDateTime.now().minusMinutes(15);
        List<SecurityEvent> failedLogins = securityEventRepository.findFailedLoginsFromIp(null, since);
        
        // Group by IP
        Map<String, Long> attemptsByIp = failedLogins.stream()
            .filter(e -> e.getIpAddress() != null)
            .collect(Collectors.groupingBy(SecurityEvent::getIpAddress, Collectors.counting()));
        
        for (Map.Entry<String, Long> entry : attemptsByIp.entrySet()) {
            if (entry.getValue() >= 10) { // More than 10 failed attempts in 15 minutes
                log.warn("Possible brute force attack from IP: {} - {} attempts", 
                    entry.getKey(), entry.getValue());
                
                blockIP(entry.getKey(), "Brute force detection - " + entry.getValue() + " failed attempts");
            }
        }
    }
    
    private void checkForSuspiciousActivity(SecurityEvent event) {
        if (event.getEventType() == SecurityEvent.SecurityEventType.LOGIN_FAILED) {
            // Check for multiple failed logins from same IP
            LocalDateTime since = LocalDateTime.now().minusHours(1);
            long failedCount = securityEventRepository.countFailedLoginsForUser(
                event.getUsername(), since);
            
            if (failedCount >= 5) {
                log.warn("Multiple failed logins for user: {} - {} attempts in last hour",
                    event.getUsername(), failedCount);
                
                // Could trigger account lockout
            }
        }
    }
    
    private HttpServletRequest getCurrentHttpRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }
    
    private UUID getCurrentUserId() {
        // In a real implementation, get from SecurityContext
        return UUID.randomUUID();
    }
    
    private SecurityEventDTO mapToDTO(SecurityEvent event) {
        return SecurityEventDTO.builder()
            .id(event.getId())
            .timestamp(event.getTimestamp())
            .eventType(event.getEventType().toString())
            .severity(event.getSeverity() != null ? event.getSeverity().toString() : null)
            .userId(event.getUserId())
            .username(event.getUsername())
            .ipAddress(event.getIpAddress())
            .userAgent(event.getUserAgent())
            .location(event.getLocation())
            .details(event.getDetails())
            .blocked(event.getBlocked())
            .blockReason(event.getBlockReason())
            .resolvedAt(event.getResolvedAt())
            .resolvedBy(event.getResolvedBy() != null ? event.getResolvedBy().toString() : null)
            .build();
    }
}
