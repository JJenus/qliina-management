package com.jjenus.qliina_management.identity.model;

import com.jjenus.qliina_management.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "permissions")
@Getter
@Setter
public class Permission extends BaseEntity {
    @Column(nullable = false, unique = true)
    private String name;

    @Column(name = "display_name")
    private String displayName;

    private String description;

    private String category;

    @Column(name = "scope")
    @Enumerated(EnumType.STRING)
    private PermissionScope scope;

    @Column(name = "is_default")
    private Boolean isDefault = false;

    public enum PermissionScope {
        GLOBAL, BUSINESS, SHOP
    }
}
