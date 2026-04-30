package com.jjenus.qliina_management.business.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateServiceTypeRequest {
    @NotBlank(message = "Service name is required")
    private String name;
    private String description;
    @NotBlank(message = "Category is required")
    private String category;
    @NotNull(message = "Default price is required")
    private BigDecimal defaultPrice;
    private String unit = "PER_ITEM";
    private Integer sortOrder;
    private String icon;
}