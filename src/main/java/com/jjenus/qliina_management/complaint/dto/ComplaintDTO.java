package com.jjenus.qliina_management.complaint.dto;

import com.jjenus.qliina_management.complaint.model.ComplaintCategory;
import com.jjenus.qliina_management.complaint.model.ComplaintSeverity;
import com.jjenus.qliina_management.complaint.model.ComplaintStatus;
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
public class ComplaintDTO {
    private UUID id;
    private UUID businessId;
    private UUID shopId;
    private String complaintNumber;
    private String customerName;
    private ComplaintCategory category;
    private ComplaintSeverity severity;
    private String description;
    private ComplaintStatus status;
    private LocalDateTime slaDueAt;
    /** True when the ticket is still open and its SLA window has elapsed. */
    private boolean overdue;
    private LocalDateTime resolvedAt;
    private String resolutionNote;
    private UUID resolvedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}