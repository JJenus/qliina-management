package com.jjenus.qliina_management.identity.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserShopAssignmentDTO {
    private UUID shopId;
    private String shopName;
    private Boolean isPrimary;
    private List<RoleInfo> roles;
    private List<PermissionInfo> permissions;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RoleInfo {
        private UUID roleId;
        private String roleName;
        private LocalDateTime assignedAt;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PermissionInfo {
        private UUID permissionId;
        private String permissionName;
        private String source; // ROLE or DIRECT
        private LocalDateTime grantedAt;
    }
}
