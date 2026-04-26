package com.jjenus.qliina_management.business.model;

import com.jjenus.qliina_management.common.BaseEntity;
import com.jjenus.qliina_management.identity.model.Address;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * First-class tenant entity representing a laundry business on the platform.
 *
 * Every tenant object (Shop, User, Order, etc.) carries a businessId UUID.
 * This entity is the authoritative row that UUID must resolve to. Without it
 * multi-tenancy has no referential integrity.
 *
 * Lifecycle: created via POST /api/v1/auth/register-business; may be
 * suspended or cancelled by a Superadmin via
 * PATCH /api/v1/businesses/{id}/status.
 */
@Entity
@Table(name = "businesses", indexes = {
    @Index(name = "idx_business_slug",   columnList = "slug",   unique = true),
    @Index(name = "idx_business_status", columnList = "status"),
    @Index(name = "idx_business_plan",   columnList = "plan")
})
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Business extends BaseEntity {

    /** Human-readable display name, e.g. "Sunshine Laundry". */
    @Column(name = "name", nullable = false, length = 120)
    private String name;

    /**
     * URL-safe unique identifier derived from the business name at registration
     * (e.g. "sunshine-laundry-1a2b"). Used in subdomain routing and deep links.
     */
    @Column(name = "slug", nullable = false, unique = true, length = 80)
    private String slug;

    /** Lifecycle status of the business account. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status;

    /** Subscription / feature tier. */
    @Enumerated(EnumType.STRING)
    @Column(name = "plan", nullable = false, length = 20)
    private Plan plan;

    /** Primary contact email for the business (not the owner login email). */
    @Column(name = "email", length = 120)
    private String email;

    /** Primary contact phone for the business. */
    @Column(name = "phone", length = 20)
    private String phone;

    /** URL to the business logo stored in object storage. */
    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    /** For TRIAL accounts: when the free trial expires. */
    @Column(name = "trial_ends_at")
    private LocalDateTime trialEndsAt;

    /** Physical address of the business headquarters. */
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "addressLine1", column = @Column(name = "address_line1")),
        @AttributeOverride(name = "addressLine2", column = @Column(name = "address_line2")),
        @AttributeOverride(name = "city",         column = @Column(name = "city")),
        @AttributeOverride(name = "state",        column = @Column(name = "state")),
        @AttributeOverride(name = "postalCode",   column = @Column(name = "postal_code")),
        @AttributeOverride(name = "country",      column = @Column(name = "country")),
        @AttributeOverride(name = "latitude",     column = @Column(name = "latitude")),
        @AttributeOverride(name = "longitude",    column = @Column(name = "longitude"))
    })
    private Address address;

    /**
     * Lifecycle states:
     *   TRIAL     – free trial; limited access until trialEndsAt.
     *   ACTIVE    – fully subscribed and operational.
     *   SUSPENDED – temporarily disabled (non-payment / policy violation).
     *   CANCELLED – permanently closed; data retained per retention policy.
     */
    public enum Status { TRIAL, ACTIVE, SUSPENDED, CANCELLED }

    /**
     * Feature / pricing tier:
     *   FREE       – single shop, basic features.
     *   STARTER    – up to 3 shops, standard features.
     *   PRO        – unlimited shops, advanced analytics.
     *   ENTERPRISE – custom SLA, dedicated support (platform internal).
     */
    public enum Plan { FREE, STARTER, PRO, ENTERPRISE }
}
