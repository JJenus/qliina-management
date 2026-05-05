 // ./src/main/java/com/jjenus/qliina_management/audit/dto/ItemAuditEntryDTO.java
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
public class ItemAuditEntryDTO {
    
    private UUID id;
    private LocalDateTime timestamp;
    private String action;          // e.g., "STATUS_CHANGE", "QC_CHECK", "DEFECT_REPORTED"
    private UUID actorId;
    private String actorName;
    private String oldStatus;
    private String newStatus;
    private String details;
}