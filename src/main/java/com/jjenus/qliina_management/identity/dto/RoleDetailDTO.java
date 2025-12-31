package com.jjenus.qliina_management.identity.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class RoleDetailDTO extends RoleDTO {
    private List<PermissionDTO> permissions;
    private MetadataDTO metadata;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MetadataDTO {
        private UUID createdBy;
        private LocalDateTime createdAt;
        private UUID updatedBy;
        private LocalDateTime updatedAt;
    }
}
