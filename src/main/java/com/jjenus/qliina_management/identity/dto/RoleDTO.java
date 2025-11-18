package com.jjenus.qliina_management.identity.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.experimental.SuperBuilder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class RoleDTO {
    private UUID id;
    private String name;
    private String description;
    private String type; // PLATFORM, BUSINESS, CUSTOM
    private UUID businessId;
    private Boolean isSystem;
    private Long userCount;
    private LocalDateTime createdAt;
}
