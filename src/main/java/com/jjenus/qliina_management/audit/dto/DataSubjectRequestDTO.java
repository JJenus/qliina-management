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
public class DataSubjectRequestDTO {
    private UUID id;
    private String requestNumber;
    private UUID customerId;
    private String customerName;
    private String customerEmail;
    private String requestType;
    private String status;
    private LocalDateTime submittedAt;
    private LocalDateTime dueDate;
    private LocalDateTime completedAt;
    private String completedBy;
    private String requestDetails;
    private String responseDetails;
    private String dataExportUrl;
    private Boolean verified;
    private String assignedTo;
}
