package com.jjenus.qliina_management.identity.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPermissionsDTO {
    private UUID userId;
    private UUID businessId;
    private List<RolePermissionDTO> rolePermissions;
    private List<String> directPermissions;
    private List<String> effectivePermissions;
    private List<PermissionDetailDTO> permissionDetails;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RolePermissionDTO {
        private String role;
        private List<String> permissions;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PermissionDetailDTO {
        private String name;
        private String source; // ROLE or DIRECT
        private UUID sourceId;
        private String scope; // BUSINESS or SHOP
        private UUID shopId;
    }
}
