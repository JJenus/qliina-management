package com.jjenus.qliina_management.identity.dto;

import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class AddPermissionsRequest {
    private List<UUID> permissionIds;
}
