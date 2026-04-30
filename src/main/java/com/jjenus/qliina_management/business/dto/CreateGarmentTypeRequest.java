package com.jjenus.qliina_management.business.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateGarmentTypeRequest {
    @NotBlank(message = "Garment name is required")
    private String name;
    private String description;
    @NotBlank(message = "Category is required")
    private String category;
    private Integer sortOrder;
    private String icon;
}