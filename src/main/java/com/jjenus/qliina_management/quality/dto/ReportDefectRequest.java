package com.jjenus.qliina_management.quality.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class ReportDefectRequest {
    @NotBlank(message = "Defect type is required")
    private String type;
    
    @NotBlank(message = "Severity is required")
    private String severity;
    
    @NotBlank(message = "Description is required")
    private String description;
    
    private List<String> images;
    
    private String location;
}
