package com.jjenus.qliina_management.audit.dto;

import lombok.Data;
import java.util.UUID;

@Data
public class UpdateDataSubjectRequest {
    private String status;
    private String responseDetails;
    private String dataExportUrl;
    private Boolean verified;
    private String notes;
    private UUID assignedTo;
}
