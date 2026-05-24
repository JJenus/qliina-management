package com.jjenus.qliina_management.order.model;

import com.jjenus.qliina_management.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Represents one physical unit within a multi-quantity item line.
 *
 * When an OrderItem has quantity > 1 (e.g. 3 shirts), three OrderItemUnit
 * records are created with unit_number 1, 2, 3 and individual barcodes
 * such as "QL-FX78GLJ6-01", "QL-FX78GLJ6-02", "QL-FX78GLJ6-03".
 *
 * Workers can scan or type the short form "FX78GLJ6-01" to look up a specific
 * piece without ambiguity across multiple quantity items.
 */
@Entity
@Table(
    name = "order_item_units",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_item_unit",
        columnNames = {"order_item_id", "unit_number"}
    ),
    indexes = {
        @Index(name = "idx_unit_barcode", columnList = "barcode"),
        @Index(name = "idx_unit_item",    columnList = "order_item_id")
    }
)
@Getter
@Setter
public class OrderItemUnit extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_item_id", nullable = false)
    private OrderItem orderItem;

    /** 1-based unit number within the item line (1, 2, 3, …). */
    @Column(name = "unit_number", nullable = false)
    private Integer unitNumber;

    /**
     * Full scannable barcode, e.g. "QL-FX78GLJ6-01".
     * Unique across the system so any scanner hit can locate the item.
     */
    @Column(name = "barcode", length = 30, unique = true)
    private String barcode;

    /** Processing status of this individual unit. Defaults to PENDING. */
    @Column(name = "status", length = 50)
    private String status = "PENDING";

    /** Optional notes (damage, re-inspection reason, etc.). */
    @Column(name = "notes")
    private String notes;
}
