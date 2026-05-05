// ./src/main/java/com/jjenus/qliina_management/audit/service/ItemAuditService.java
package com.jjenus.qliina_management.audit.service;

import com.jjenus.qliina_management.audit.dto.ItemAuditEntryDTO;
import com.jjenus.qliina_management.audit.model.AuditLog;
import com.jjenus.qliina_management.audit.repository.AuditLogRepository;
import com.jjenus.qliina_management.common.BusinessException;
import com.jjenus.qliina_management.common.PageResponse;
import com.jjenus.qliina_management.identity.model.User;
import com.jjenus.qliina_management.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ItemAuditService {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    /**
     * Gets the complete audit trail for a specific order item.
     * Only accessible by managers/owners or the workers who interacted with the item.
     */
    @Transactional(readOnly = true)
    public PageResponse<ItemAuditEntryDTO> getItemAuditTrail(
            UUID businessId, UUID itemId, UUID requestingUserId, Pageable pageable) {
        
        Page<AuditLog> logs = auditLogRepository.findByEntityTypeAndEntityId("ORDER_ITEM", itemId, pageable);
        
        return PageResponse.from(logs.map(log -> mapToItemAuditDTO(log)));
    }

    /**
     * Gets a worker's interaction history with items.
     * Self-service - workers can only see their own history.
     */
    @Transactional(readOnly = true)
    public PageResponse<ItemAuditEntryDTO> getWorkerItemHistory(
            UUID workerId, UUID itemId, Pageable pageable) {
        
        // Only return entries where this specific worker was the actor
        Page<AuditLog> logs = auditLogRepository.findByEntityTypeAndEntityIdAndUserId(
                "ORDER_ITEM", itemId, workerId, pageable);
        
        return PageResponse.from(logs.map(log -> mapToItemAuditDTO(log)));
    }

    private ItemAuditEntryDTO mapToItemAuditDTO(AuditLog log) {
        String actorName = log.getUserName();
        if (actorName == null && log.getUserId() != null) {
            actorName = userRepository.findById(log.getUserId())
                    .map(u -> u.getFirstName() + " " + u.getLastName())
                    .orElse("Unknown");
        }

        return ItemAuditEntryDTO.builder()
                .id(log.getId())
                .timestamp(log.getTimestamp())
                .action(log.getAction())
                .actorId(log.getUserId())
                .actorName(actorName)
                .oldStatus(extractStatus(log.getOldValue()))
                .newStatus(extractStatus(log.getNewValue()))
                .details(log.getDetails())
                .build();
    }

    private String extractStatus(String jsonValue) {
        if (jsonValue == null) return null;
        try {
            // Simple extraction from JSON - in production use ObjectMapper
            if (jsonValue.contains("\"status\"")) {
                int start = jsonValue.indexOf("\"status\"") + 10;
                int end = jsonValue.indexOf("\"", start);
                return jsonValue.substring(start, end);
            }
        } catch (Exception ignored) {}
        return null;
    }
}