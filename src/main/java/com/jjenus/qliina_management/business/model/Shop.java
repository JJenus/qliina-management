package com.jjenus.qliina_management.business.model;

import com.jjenus.qliina_management.common.BaseTenantEntity;
import com.jjenus.qliina_management.identity.model.Address;
import com.jjenus.qliina_management.identity.model.OperatingHour;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.ArrayList;
import java.util.List;

/**
 * A physical or virtual location belonging to a Business.
 *
 * A business may have one or more shops. Every operational entity
 * (Order, CashDrawerSession, Inventory, etc.) is scoped to a shop via shopId.
 *
 * Module note: moved from identity to business so all tenant-structure entities
 * live in one place. The identity package retains a thin compatibility stub so
 * existing callers compile without import changes.
 */
@Entity
@Table(name = "shops", indexes = {
    @Index(name = "idx_shop_code",     columnList = "code",        unique = true),
    @Index(name = "idx_shop_business", columnList = "business_id"),
    @Index(name = "idx_shop_active",   columnList = "active")
})
@Getter @Setter
public class Shop extends BaseTenantEntity {

    /** Display name of the shop, e.g. "Downtown Branch". */
    @Column(nullable = false)
    private String name;

    /**
     * Short unique uppercase code used in order numbers and UI selectors,
     * e.g. "MAIN" or "EAST-01". Immutable after creation.
     */
    @Column(nullable = false, unique = true)
    private String code;

    /** Physical address of this shop location. */
    @Embedded
    private Address address;

    /** Contact phone number for this shop location. */
    private String phone;

    /** Contact email for this shop location. */
    private String email;

    /**
     * IANA timezone identifier, e.g. "America/New_York".
     * Used for report generation and notification scheduling.
     */
    private String timezone;

    /** Whether this shop is currently accepting orders. */
    private Boolean active = true;

    /** Weekly operating hours; stored as an element collection. */
    @ElementCollection
    @CollectionTable(name = "shop_operating_hours", joinColumns = @JoinColumn(name = "shop_id"))
    private List<OperatingHour> operatingHours = new ArrayList<>();
}
