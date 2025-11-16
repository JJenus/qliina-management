package com.jjenus.qliina_management.identity.model;

import com.jjenus.qliina_management.common.BaseTenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "users", indexes = {
    @Index(name = "idx_user_username", columnList = "username", unique = true),
    @Index(name = "idx_user_email", columnList = "email"),
    @Index(name = "idx_user_phone", columnList = "phone")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User extends BaseTenantEntity {
    @Column(nullable = false, unique = true)
    private String username;

    @Column(name = "email")
    private String email;

    @Column(name = "phone", nullable = false)
    private String phone;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(nullable = false)
    private Boolean enabled = true;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private AuthAccount authAccount;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<UserRole> roles = new HashSet<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<UserPermission> directPermissions = new HashSet<>();

    @ManyToMany
    @JoinTable(
        name = "user_shops",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "shop_id")
    )
    private Set<Shop> shops = new HashSet<>();

    @Column(name = "primary_shop_id")
    private UUID primaryShopId;

    @Column(name = "last_login")
    private LocalDateTime lastLogin;

    @Column(name = "profile_image")
    private String profileImage;
}
