package com.jjenus.qliina_management.identity.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PermissionDTO {
    private UUID id;
    private String name;
    private String displayName;
    private String description;
    private String category;
    private String scope; // GLOBAL, BUSINESS, SHOP
    private Boolean isDefault;
}
