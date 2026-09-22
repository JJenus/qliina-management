package com.jjenus.qliina_management.complaint.dto;

import com.jjenus.qliina_management.complaint.model.ComplaintStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateComplaintStatusRequest {

    @NotNull(message = "Status is required")
    private ComplaintStatus status;

    /** Required when moving to RESOLVED or REJECTED (terminal, audit-facing). */
    private String resolutionNote;
}