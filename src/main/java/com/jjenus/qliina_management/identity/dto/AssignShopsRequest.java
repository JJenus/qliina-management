package com.jjenus.qliina_management.identity.dto;

import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class AssignShopsRequest {
    private List<ShopAssignment> shopAssignments;
    
    @Data
    public static class ShopAssignment {
        private UUID shopId;
        private Boolean isPrimary;
        private List<UUID> roles;
    }
}
