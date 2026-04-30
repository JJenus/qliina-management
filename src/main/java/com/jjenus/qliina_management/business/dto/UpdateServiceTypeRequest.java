package com.jjenus.qliina_management.business.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class UpdateServiceTypeRequest {
    private String name;
    private String description;
    private String category;
    private BigDecimal defaultPrice;
    private String unit;
    private Boolean isActive;
    private Integer sortOrder;
    private String icon;
}