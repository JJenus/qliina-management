package com.jjenus.qliina_management.identity.dto;

import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class AssignRolesRequest {
    private List<RoleAssignment> roles;
    
    @Data
    public static class RoleAssignment {
        private UUID roleId;
        private UUID shopId;
    }
}
