package com.jjenus.qliina_management.identity.model;

import com.jjenus.qliina_management.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(
    name = "user_roles",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_user_role_scope",
        columnNames = {"user_id", "role_id", "business_id", "shop_id"}
    )
)
@Getter
@Setter
public class UserRole extends BaseEntity {

    // Sentinel value for "no shop" (business-level or platform-level role)
    public static final UUID GLOBAL_SCOPE = new UUID(0L, 0L);

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "shop_id", nullable = false)
    private UUID shopId;

    @PrePersist
    public void normalizeShopScope() {
        if (this.shopId == null) {
            this.shopId = GLOBAL_SCOPE;
        }
    }

    // Helper to normalize shop scope (still useful for explicit calls)
    public void setShopScope(UUID shopId) {
        this.shopId = (shopId != null) ? shopId : GLOBAL_SCOPE;
    }

    // optional helper (cleaner reads)
    public boolean isGlobalScope() {
        return GLOBAL_SCOPE.equals(this.shopId);
    }
}