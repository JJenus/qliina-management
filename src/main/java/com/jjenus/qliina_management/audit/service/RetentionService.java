package com.jjenus.qliina_management.audit.service;

import com.jjenus.qliina_management.audit.dto.CleanupResultDTO;
import com.jjenus.qliina_management.audit.dto.CreateRetentionPolicyRequest;
import com.jjenus.qliina_management.audit.dto.DataRetentionPolicyDTO;
import com.jjenus.qliina_management.audit.model.AuditLog;
import com.jjenus.qliina_management.audit.model.DataRetentionPolicy;
import com.jjenus.qliina_management.audit.repository.AuditLogRepository;
import com.jjenus.qliina_management.audit.repository.DataRetentionPolicyRepository;
import com.jjenus.qliina_management.common.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RetentionService {
    
    private final DataRetentionPolicyRepository policyRepository;
    private final AuditLogRepository auditLogRepository;
    
    @Transactional(readOnly = true)
    public List<DataRetentionPolicyDTO> getPolicies(UUID businessId) {
        return policyRepository.findByBusinessId(businessId).stream()
            .map(this::mapToDTO)
            .collect(Collectors.toList());
    }
    
    @Transactional
    public DataRetentionPolicyDTO createPolicy(UUID businessId, CreateRetentionPolicyRequest request) {
        // Check if policy already exists for this entity type
        policyRepository.findByBusinessIdAndEntityType(businessId, request.getEntityType())
            .ifPresent(p -> {
                throw new BusinessException("Policy already exists for entity type: " + request.getEntityType(), 
                    "POLICY_EXISTS");
            });
        
        DataRetentionPolicy policy = new DataRetentionPolicy();
        policy.setBusinessId(businessId);
        policy.setEntityType(request.getEntityType());
        policy.setRetentionDays(request.getRetentionDays());
        policy.setArchiveEnabled(request.getArchiveEnabled() != null ? request.getArchiveEnabled() : false);
        policy.setArchiveLocation(request.getArchiveLocation());
        policy.setDeleteEnabled(request.getDeleteEnabled() != null ? request.getDeleteEnabled() : true);
        policy.setNotificationDaysBefore(request.getNotificationDaysBefore());
        policy.setIsActive(true);
        policy.setNextRun(LocalDateTime.now().plusDays(1)); // Check tomorrow
        
        policy = policyRepository.save(policy);
        return mapToDTO(policy);
    }
    
    @Transactional
    public DataRetentionPolicyDTO updatePolicy(UUID policyId, CreateRetentionPolicyRequest request) {
        DataRetentionPolicy policy = policyRepository.findById(policyId)
            .orElseThrow(() -> new BusinessException("Policy not found", "POLICY_NOT_FOUND"));
        
        policy.setRetentionDays(request.getRetentionDays());
        policy.setArchiveEnabled(request.getArchiveEnabled() != null ? request.getArchiveEnabled() : policy.getArchiveEnabled());
        policy.setArchiveLocation(request.getArchiveLocation());
        policy.setDeleteEnabled(request.getDeleteEnabled() != null ? request.getDeleteEnabled() : policy.getDeleteEnabled());
        policy.setNotificationDaysBefore(request.getNotificationDaysBefore());
        
        policy = policyRepository.save(policy);
        return mapToDTO(policy);
    }
    
    @Transactional
    public void deletePolicy(UUID policyId) {
        DataRetentionPolicy policy = policyRepository.findById(policyId)
            .orElseThrow(() -> new BusinessException("Policy not found", "POLICY_NOT_FOUND"));
        policyRepository.delete(policy);
    }
    
    @Transactional
    public CleanupResultDTO triggerCleanup(UUID businessId) {
        Map<String, Long> recordsDeleted = new HashMap<>();
        Map<String, Long> recordsArchived = new HashMap<>();
        long startTime = System.currentTimeMillis();
        long totalProcessed = 0;
        
        List<DataRetentionPolicy> policies = policyRepository.findActivePolicies(businessId);
        
        for (DataRetentionPolicy policy : policies) {
            try {
                LocalDateTime cutoffDate = LocalDateTime.now().minusDays(policy.getRetentionDays());
                
                // Find expired logs for this entity type
                List<AuditLog> expiredLogs = auditLogRepository.findByBusinessIdAndEntityTypeAndTimestampBefore(
                    businessId, policy.getEntityType(), cutoffDate);
                
                if (expiredLogs.isEmpty()) {
                    continue;
                }
                
                if (policy.getArchiveEnabled()) {
                    // Archive logic would go here - export to cold storage
                    int archived = archiveRecords(expiredLogs, policy);
                    recordsArchived.put(policy.getEntityType(), (long) archived);
                }
                
                if (policy.getDeleteEnabled()) {
                    // Delete expired logs
                    List<UUID> idsToDelete = expiredLogs.stream()
                        .map(AuditLog::getId)
                        .collect(Collectors.toList());
                    
                    auditLogRepository.deleteByIds(idsToDelete);
                    recordsDeleted.put(policy.getEntityType(), (long) idsToDelete.size());
                    totalProcessed += idsToDelete.size();
                }
                
                // Update policy last run
                policy.setLastRun(LocalDateTime.now());
                policy.setNextRun(LocalDateTime.now().plusDays(1));
                policyRepository.save(policy);
                
            } catch (Exception e) {
                log.error("Failed to process retention policy: {}", policy.getId(), e);
            }
        }
        
        long executionTime = System.currentTimeMillis() - startTime;
        
        return CleanupResultDTO.builder()
            .recordsDeleted(recordsDeleted)
            .recordsArchived(recordsArchived)
            .totalRecordsProcessed(totalProcessed)
            .executionTimeMs(executionTime)
            .status("COMPLETED")
            .message("Cleanup completed successfully")
            .build();
    }
    
    @Scheduled(cron = "0 0 2 * * *") // Run at 2 AM daily
    @Transactional
    public void scheduledCleanup() {
        log.info("Starting scheduled data retention cleanup");
        
        List<DataRetentionPolicy> policies = policyRepository.findPoliciesDueForExecution(LocalDateTime.now());
        
        for (DataRetentionPolicy policy : policies) {
            try {
                triggerCleanup(policy.getBusinessId());
            } catch (Exception e) {
                log.error("Failed scheduled cleanup for policy: {}", policy.getId(), e);
            }
        }
    }
    
    private int archiveRecords(List<AuditLog> records, DataRetentionPolicy policy) {
        // Implementation for archiving to cold storage (S3, etc.)
        // This would typically write to a file and upload to cloud storage
        log.info("Archiving {} records for entity type: {}", records.size(), policy.getEntityType());
        return records.size();
    }
    
    private DataRetentionPolicyDTO mapToDTO(DataRetentionPolicy policy) {
        return DataRetentionPolicyDTO.builder()
            .id(policy.getId())
            .entityType(policy.getEntityType())
            .retentionDays(policy.getRetentionDays())
            .archiveEnabled(policy.getArchiveEnabled())
            .archiveLocation(policy.getArchiveLocation())
            .deleteEnabled(policy.getDeleteEnabled())
            .notificationDaysBefore(policy.getNotificationDaysBefore())
            .lastRun(policy.getLastRun())
            .nextRun(policy.getNextRun())
            .isActive(policy.getIsActive())
            .build();
    }
}
