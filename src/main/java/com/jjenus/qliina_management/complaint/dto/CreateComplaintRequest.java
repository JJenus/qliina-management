package com.jjenus.qliina_management.complaint.dto;

import com.jjenus.qliina_management.complaint.model.ComplaintCategory;
import com.jjenus.qliina_management.complaint.model.ComplaintSeverity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateComplaintRequest {

    @NotBlank(message = "Customer name is required")
    private String customerName;

    @NotNull(message = "Category is required")
    private ComplaintCategory category;

    @NotNull(message = "Severity is required")
    private ComplaintSeverity severity;

    @NotBlank(message = "Description is required")
    private String description;

    /** Optional shop scope. */
    private UUID shopId;
}