package com.jjenus.qliina_management.complaint.model;

import com.jjenus.qliina_management.common.BaseTenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A customer complaint / support ticket, logged against a business (and
 * optionally a shop). Business-scope domain: the business UI can create and
 * track complaints; the platform inbox can triage them across tenants.
 */
@Entity
@Table(name = "complaints", indexes = {
    @Index(name = "idx_complaint_business_status", columnList = "business_id, status"),
    @Index(name = "idx_complaint_business_created", columnList = "business_id, created_at")
})
@Getter
@Setter
public class Complaint extends BaseTenantEntity {

    /** Human-friendly sequential reference, e.g. CMP-00001 (per business). */
    @Column(name = "complaint_number", nullable = false, length = 30)
    private String complaintNumber;

    @Column(name = "customer_name", nullable = false)
    private String customerName;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 30)
    private ComplaintCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 30)
    private ComplaintSeverity severity;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ComplaintStatus status = ComplaintStatus.OPEN;

    /** Target resolution time computed from severity SLA on creation. */
    @Column(name = "sla_due_at", nullable = false)
    private LocalDateTime slaDueAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "resolution_note", columnDefinition = "TEXT")
    private String resolutionNote;

    @Column(name = "resolved_by")
    private UUID resolvedBy;
}