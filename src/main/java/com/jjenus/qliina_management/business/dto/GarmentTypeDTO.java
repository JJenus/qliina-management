package com.jjenus.qliina_management.business.dto;

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
public class GarmentTypeDTO {
    private UUID id;
    private String name;
    private String description;
    private String category;
    private Boolean isActive;
    private Integer sortOrder;
    private String icon;
    private LocalDateTime createdAt;
}