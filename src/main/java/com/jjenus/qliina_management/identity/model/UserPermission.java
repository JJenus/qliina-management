package com.jjenus.qliina_management.identity.model;

import com.jjenus.qliina_management.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_permissions")
@Getter
@Setter
public class UserPermission extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "permission_id", nullable = false)
    private Permission permission;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "shop_id")
    private UUID shopId; // null for business-level

    @Column(name = "granted_at")
    private LocalDateTime grantedAt;

    @Column(name = "granted_by")
    private UUID grantedBy;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;
}
