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
public class UserDetailDTO extends UserSummaryDTO {
    private UUID businessId;
    private List<ShopAssignmentDTO> assignedShops;
    private List<DirectPermissionDTO> directPermissions;
    private MetadataDTO metadata;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ShopAssignmentDTO {
        private UUID shopId;
        private String shopName;
        private Boolean isPrimary;
        private List<String> roles;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DirectPermissionDTO {
        private UUID permissionId;
        private String permissionName;
        private LocalDateTime grantedAt;
        private String grantedBy;
        private LocalDateTime expiresAt;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MetadataDTO {
        private UUID createdBy;
        private LocalDateTime createdAt;
        private UUID updatedBy;
        private LocalDateTime updatedAt;
        private String lastLoginIp;
        private Integer failedLoginAttempts;
    }
}
