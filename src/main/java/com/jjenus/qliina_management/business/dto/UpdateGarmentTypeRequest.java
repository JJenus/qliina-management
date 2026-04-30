package com.jjenus.qliina_management.business.dto;

import lombok.Data;

@Data
public class UpdateGarmentTypeRequest {
    private String name;
    private String description;
    private String category;
    private Boolean isActive;
    private Integer sortOrder;
    private String icon;
}