package com.jjenus.qliina_management.business.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceTypeDTO {
    private UUID id;
    private String name;
    private String description;
    private String category;
    private BigDecimal defaultPrice;
    private String unit;
    private Boolean isActive;
    private Integer sortOrder;
    private String icon;
    private LocalDateTime createdAt;
}