package com.jjenus.qliina_management.audit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjenus.qliina_management.audit.dto.*;
import com.jjenus.qliina_management.audit.model.AuditLog;
import com.jjenus.qliina_management.audit.repository.AuditLogRepository;
import com.jjenus.qliina_management.common.PageResponse;
import com.jjenus.qliina_management.identity.model.User;
import com.jjenus.qliina_management.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {
    
    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    
    @Transactional
    public void logEvent(String entityType, UUID entityId, String action, 
                          Object oldValue, Object newValue, String details) {
        try {
            HttpServletRequest request = getCurrentHttpRequest();
            User currentUser = getCurrentUser();
            
            AuditLog.AuditSeverity severity = determineSeverity(action, oldValue, newValue);
            
            Map<String, AuditLog.ChangeDetail> changes = calculateChanges(oldValue, newValue);
            
            AuditLog auditLog = AuditLog.builder()
                .timestamp(LocalDateTime.now())
                .userId(currentUser != null ? currentUser.getId() : null)
                .userName(currentUser != null ? currentUser.getFirstName() + " " + currentUser.getLastName() : "System")
                .userEmail(currentUser != null ? currentUser.getEmail() : null)
                .businessId(currentUser != null ? currentUser.getBusinessId() : null)
                .entityType(entityType)
                .entityId(entityId)
                .entityDisplay(getEntityDisplay(entityType, entityId))
                .action(action)
                .category(determineCategory(entityType, action))
                .severity(severity)
                .oldValue(oldValue != null ? objectMapper.writeValueAsString(oldValue) : null)
                .newValue(newValue != null ? objectMapper.writeValueAsString(newValue) : null)
                .changes(changes)
                .ipAddress(request != null ? request.getRemoteAddr() : null)
                .userAgent(request != null ? request.getHeader("User-Agent") : null)
                .sessionId(request != null ? request.getSession().getId() : null)
                .requestId(UUID.randomUUID().toString())
                .requestPath(request != null ? request.getRequestURI() : null)
                .requestMethod(request != null ? request.getMethod() : null)
                .details(details)
                .retentionUntil(LocalDateTime.now().plusYears(7)) // Default 7-year retention
                .build();
            
            auditLogRepository.save(auditLog);
            
        } catch (Exception e) {
            log.error("Failed to create audit log", e);
        }
    }
    
    @Transactional
    public void logLogin(UUID userId, boolean success, String ipAddress, String userAgent) {
        User user = userRepository.findById(userId).orElse(null);
        
        AuditLog auditLog = AuditLog.builder()
            .timestamp(LocalDateTime.now())
            .userId(userId)
            .userName(user != null ? user.getFirstName() + " " + user.getLastName() : null)
            .userEmail(user != null ? user.getEmail() : null)
            .businessId(user != null ? user.getBusinessId() : null)
            .entityType("AUTH")
            .action(success ? "LOGIN_SUCCESS" : "LOGIN_FAILED")
            .category("AUTHENTICATION")
            .severity(success ? AuditLog.AuditSeverity.INFO : AuditLog.AuditSeverity.WARNING)
            .ipAddress(ipAddress)
            .userAgent(userAgent)
            .details(success ? "User logged in successfully" : "Failed login attempt")
            .retentionUntil(LocalDateTime.now().plusYears(7))
            .build();
        
        auditLogRepository.save(auditLog);
    }
    
    @Transactional(readOnly = true)
    public PageResponse<AuditLogDTO> getAuditLogs(UUID businessId, AuditLogFilter filter, Pageable pageable) {
        AuditLog.AuditSeverity severity = filter.getSeverity() != null ?
            AuditLog.AuditSeverity.valueOf(filter.getSeverity()) : null;
        
        Page<AuditLog> page = auditLogRepository.searchAuditLogs(
            businessId,
            filter.getUserId(),
            filter.getEntityType(),
            filter.getEntityId(),
            filter.getAction(),
            filter.getCategory(),
            severity,
            filter.getFromDate(),
            filter.getToDate(),
            filter.getIpAddress(),
            pageable
        );
        
        return PageResponse.from(page.map(this::mapToDTO));
    }
    
    @Transactional(readOnly = true)
    public PageResponse<AuditLogDTO> getEntityHistory(String entityType, UUID entityId, Pageable pageable) {
        Page<AuditLog> page = auditLogRepository.findByEntityTypeAndEntityId(entityType, entityId, pageable);
        return PageResponse.from(page.map(this::mapToDTO));
    }
    
    @Transactional(readOnly = true)
    public PageResponse<AuditLogDTO> getUserActivity(UUID userId, Pageable pageable) {
        Page<AuditLog> page = auditLogRepository.findByUserId(userId, pageable);
        return PageResponse.from(page.map(this::mapToDTO));
    }
    
    @Transactional(readOnly = true)
    public AuditSummaryDTO getAuditSummary(UUID businessId, LocalDateTime startDate, LocalDateTime endDate) {
        List<AuditLog> logs = auditLogRepository.findByBusinessIdAndTimestampBetween(
            businessId, startDate, endDate);
        
        long totalEvents = logs.size();
        long criticalEvents = logs.stream()
            .filter(l -> l.getSeverity() == AuditLog.AuditSeverity.CRITICAL)
            .count();
        long warningEvents = logs.stream()
            .filter(l -> l.getSeverity() == AuditLog.AuditSeverity.WARNING)
            .count();
        
        // Top actions
        Map<String, Long> actionCounts = logs.stream()
            .collect(Collectors.groupingBy(AuditLog::getAction, Collectors.counting()));
        
        List<AuditSummaryDTO.ActivitySummaryDTO> topActions = actionCounts.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(10)
            .map(e -> AuditSummaryDTO.ActivitySummaryDTO.builder()
                .action(e.getKey())
                .count(e.getValue())
                .build())
            .collect(Collectors.toList());
        
        // Top users
        Map<UUID, Long> userCounts = logs.stream()
            .filter(l -> l.getUserId() != null)
            .collect(Collectors.groupingBy(AuditLog::getUserId, Collectors.counting()));
        
        List<AuditSummaryDTO.UserActivityDTO> topUsers = userCounts.entrySet().stream()
            .sorted(Map.Entry.<UUID, Long>comparingByValue().reversed())
            .limit(10)
            .map(e -> {
                User user = userRepository.findById(e.getKey()).orElse(null);
                return AuditSummaryDTO.UserActivityDTO.builder()
                    .userId(e.getKey())
                    .userName(user != null ? user.getFirstName() + " " + user.getLastName() : "Unknown")
                    .eventCount(e.getValue())
                    .build();
            })
            .collect(Collectors.toList());
        
        // Events by category
        Map<String, Long> byCategory = logs.stream()
            .collect(Collectors.groupingBy(
                l -> l.getCategory() != null ? l.getCategory() : "OTHER",
                Collectors.counting()
            ));
        
        // Events by severity
        Map<String, Long> bySeverity = logs.stream()
            .collect(Collectors.groupingBy(
                l -> l.getSeverity() != null ? l.getSeverity().toString() : "UNKNOWN",
                Collectors.counting()
            ));
        
        return AuditSummaryDTO.builder()
            .totalEvents(totalEvents)
            .criticalEvents(criticalEvents)
            .warningEvents(warningEvents)
            .topActions(topActions)
            .topUsers(topUsers)
            .eventsByCategory(byCategory)
            .eventsBySeverity(bySeverity)
            .build();
    }
    
    @Transactional
    public byte[] exportAuditLogs(UUID businessId, AuditLogFilter filter) {
        // Implementation for exporting audit logs to CSV/Excel
        return new byte[0];
    }
    
    private Map<String, AuditLog.ChangeDetail> calculateChanges(Object oldValue, Object newValue) {
        if (oldValue == null || newValue == null) {
            return null;
        }
        
        Map<String, AuditLog.ChangeDetail> changes = new HashMap<>();
        
        try {
            Map<String, Object> oldMap = objectMapper.convertValue(oldValue, Map.class);
            Map<String, Object> newMap = objectMapper.convertValue(newValue, Map.class);
            
            Set<String> allKeys = new HashSet<>();
            allKeys.addAll(oldMap.keySet());
            allKeys.addAll(newMap.keySet());
            
            for (String key : allKeys) {
                Object oldVal = oldMap.get(key);
                Object newVal = newMap.get(key);
                
                if (!Objects.equals(oldVal, newVal)) {
                    String changeType;
                    if (!oldMap.containsKey(key)) {
                        changeType = "ADDED";
                    } else if (!newMap.containsKey(key)) {
                        changeType = "REMOVED";
                    } else {
                        changeType = "MODIFIED";
                    }
                    
                    changes.put(key, AuditLog.ChangeDetail.builder()
                        .field(key)
                        .oldValue(oldVal)
                        .newValue(newVal)
                        .changeType(changeType)
                        .build());
                }
            }
            
        } catch (Exception e) {
            log.error("Failed to calculate changes", e);
        }
        
        return changes.isEmpty() ? null : changes;
    }
    
    private AuditLog.AuditSeverity determineSeverity(String action, Object oldValue, Object newValue) {
        if (action.contains("DELETE") || action.contains("PURGE")) {
            return AuditLog.AuditSeverity.WARNING;
        }
        if (action.contains("PAYMENT") || action.contains("REFUND")) {
            return AuditLog.AuditSeverity.INFO;
        }
        if (action.contains("LOGIN_FAILED")) {
            return AuditLog.AuditSeverity.WARNING;
        }
        return AuditLog.AuditSeverity.INFO;
    }
    
    private String determineCategory(String entityType, String action) {
        if (entityType.startsWith("ORDER")) return "ORDER_MANAGEMENT";
        if (entityType.startsWith("CUSTOMER")) return "CUSTOMER_MANAGEMENT";
        if (entityType.startsWith("USER")) return "USER_MANAGEMENT";
        if (entityType.startsWith("PAYMENT")) return "FINANCIAL";
        if (entityType.startsWith("AUTH")) return "AUTHENTICATION";
        return "GENERAL";
    }
    
    private String getEntityDisplay(String entityType, UUID entityId) {
        // In a real implementation, fetch display name based on entity type
        return entityType + ":" + (entityId != null ? entityId.toString().substring(0, 8) : "null");
    }
    
    private HttpServletRequest getCurrentHttpRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }
    
    private User getCurrentUser() {
        try {
            String username = SecurityContextHolder.getContext().getAuthentication().getName();
            return userRepository.findByUsername(username).orElse(null);
        } catch (Exception e) {
            return null;
        }
    }
    
    private AuditLogDTO mapToDTO(AuditLog auditLog) {
    Map<String, Object> oldValue = null;
    Map<String, Object> newValue = null;
    Map<String, AuditLogDTO.ChangeDetailDTO> changes = null;
    
    try {
        if (auditLog.getOldValue() != null) {
            oldValue = objectMapper.readValue(auditLog.getOldValue(), Map.class);
        }
        if (auditLog.getNewValue() != null) {
            newValue = objectMapper.readValue(auditLog.getNewValue(), Map.class);
        }
        if (auditLog.getChanges() != null) {
            changes = new HashMap<>();
            for (Map.Entry<String, AuditLog.ChangeDetail> entry : auditLog.getChanges().entrySet()) {
                changes.put(entry.getKey(), AuditLogDTO.ChangeDetailDTO.builder()
                    .field(entry.getValue().getField())
                    .oldValue(entry.getValue().getOldValue())
                    .newValue(entry.getValue().getNewValue())
                    .changeType(entry.getValue().getChangeType())
                    .build());
            }
        }
    } catch (Exception e) {
        log.error("Failed to parse audit log values", e);  // ← Now this uses the class logger
    }
    
    return AuditLogDTO.builder()
        .id(auditLog.getId())
        .timestamp(auditLog.getTimestamp())
        .userId(auditLog.getUserId())
        .userName(auditLog.getUserName())
        .userEmail(auditLog.getUserEmail())
        .businessId(auditLog.getBusinessId())
        .shopId(auditLog.getShopId())
        .entityType(auditLog.getEntityType())
        .entityId(auditLog.getEntityId())
        .entityDisplay(auditLog.getEntityDisplay())
        .action(auditLog.getAction())
        .category(auditLog.getCategory())
        .severity(auditLog.getSeverity() != null ? auditLog.getSeverity().toString() : null)
        .oldValue(oldValue)
        .newValue(newValue)
        .changes(changes)
        .ipAddress(auditLog.getIpAddress())
        .userAgent(auditLog.getUserAgent())
        .deviceId(auditLog.getDeviceId())
        .sessionId(auditLog.getSessionId())
        .requestId(auditLog.getRequestId())
        .requestPath(auditLog.getRequestPath())
        .requestMethod(auditLog.getRequestMethod())
        .responseStatus(auditLog.getResponseStatus())
        .executionTimeMs(auditLog.getExecutionTimeMs())
        .details(auditLog.getDetails())
        .build();
}
}
