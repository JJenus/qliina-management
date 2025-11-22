package com.jjenus.qliina_management.audit.model;

import com.jjenus.qliina_management.common.BaseTenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "data_subject_requests", indexes = {
    @Index(name = "idx_dsr_customer", columnList = "customer_id"),
    @Index(name = "idx_dsr_type", columnList = "request_type"),
    @Index(name = "idx_dsr_status", columnList = "status")
})
@Getter
@Setter
public class DataSubjectRequest extends BaseTenantEntity {
    
    @Column(name = "request_number", nullable = false, unique = true)
    private String requestNumber;
    
    @Column(name = "customer_id", nullable = false)
    private UUID customerId;
    
    @Column(name = "customer_name")
    private String customerName;
    
    @Column(name = "customer_email")
    private String customerEmail;
    
    @Column(name = "request_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private RequestType requestType;
    
    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private RequestStatus status;
    
    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;
    
    @Column(name = "due_date")
    private LocalDateTime dueDate;
    
    @Column(name = "completed_at")
    private LocalDateTime completedAt;
    
    @Column(name = "completed_by")
    private UUID completedBy;
    
    @Column(name = "request_details", columnDefinition = "text")
    private String requestDetails;
    
    @Column(name = "response_details", columnDefinition = "text")
    private String responseDetails;
    
    @Column(name = "data_export_url")
    private String dataExportUrl;
    
    @Column(name = "verification_method")
    private String verificationMethod;
    
    @Column(name = "verification_status")
    private Boolean verified = false;
    
    @Column(name = "notes")
    private String notes;
    
    @Column(name = "assigned_to")
    private UUID assignedTo;
    
    public enum RequestType {
        ACCESS, RECTIFICATION, ERASURE, RESTRICTION, PORTABILITY, OBJECTION
    }
    
    public enum RequestStatus {
        SUBMITTED, VERIFYING, IN_PROGRESS, COMPLETED, REJECTED, EXPIRED
    }
}
