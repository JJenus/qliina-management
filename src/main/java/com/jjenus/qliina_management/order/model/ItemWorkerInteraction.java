package com.jjenus.qliina_management.order.model;

import com.jjenus.qliina_management.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Tracks which workers have interacted with which order items.
 *
 * Why this entity exists:
 * 1. Enables O(1) lookup of "items worker X has worked on"
 * 2. Enforces access control — workers can ONLY access items they've
 *    explicitly interacted with (preventing arbitrary order browsing)
 * 3. Supports the "first touch" workflow: a worker must scan/lookup an
 *    item before they can perform actions on it
 * 4. Decouples interaction tracking from status history for performance
 *
 * This is NOT the same as ItemStatusHistory, which records every status
 * change. This records the fact that a worker accessed an item at all.
 * A worker may view an item multiple times without changing its status.
 */
@Entity
@Table(name = "item_worker_interactions", indexes = {
    @Index(name = "idx_iwi_worker", columnList = "worker_id"),
    @Index(name = "idx_iwi_item", columnList = "item_id"),
    @Index(name = "idx_iwi_worker_item", columnList = "worker_id, item_id", unique = true)
})
@Getter
@Setter
public class ItemWorkerInteraction extends BaseEntity {

    @Column(name = "worker_id", nullable = false)
    private UUID workerId;

    @Column(name = "item_id", nullable = false)
    private UUID itemId;

    @Column(name = "first_interaction", nullable = false)
    private LocalDateTime firstInteraction;

    @Column(name = "last_interaction", nullable = false)
    private LocalDateTime lastInteraction;

    @Column(name = "interaction_count")
    private Integer interactionCount = 1;

    /**
     * How the worker accessed this item:
     * SCAN - scanned QR code
     * LOOKUP - manually entered ID
     * QUEUE - picked from their work queue
     * TRANSFER - transferred from another worker
     */
    @Column(name = "first_access_method")
    private String firstAccessMethod;

    public void recordInteraction() {
        this.lastInteraction = LocalDateTime.now();
        this.interactionCount = (this.interactionCount != null ? this.interactionCount : 0) + 1;
    }
}