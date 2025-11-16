package com.jjenus.qliina_management.identity.model;

import com.jjenus.qliina_management.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "roles")
@Getter
@Setter
public class Role extends BaseEntity {
    @Column(nullable = false, unique = true)
    private String name;

    private String description;

    @Column(name = "type", nullable = false)
    @Enumerated(EnumType.STRING)
    private RoleType type;

    @Column(name = "business_id")
    private UUID businessId; // null for platform roles

    @Column(name = "is_system")
    private Boolean isSystem = false;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "role_permissions",
        joinColumns = @JoinColumn(name = "role_id"),
        inverseJoinColumns = @JoinColumn(name = "permission_id")
    )
    private Set<Permission> permissions = new HashSet<>();

    public enum RoleType {
        PLATFORM, BUSINESS, CUSTOM
    }
}
