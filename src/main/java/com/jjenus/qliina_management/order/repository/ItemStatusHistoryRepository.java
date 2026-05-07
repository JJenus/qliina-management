package com.jjenus.qliina_management.order.repository;

import com.jjenus.qliina_management.order.model.ItemStatusHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Repository for ItemStatusHistory entities.
 * 
 * ItemStatusHistory records every status change on individual order items,
 * providing a complete audit trail of who changed what and when.
 * 
 * This is separate from OrderTimeline (which tracks order-level changes)
 * and ItemWorkerInteraction (which tracks worker access to items).
 */
@Repository
public interface ItemStatusHistoryRepository extends JpaRepository<ItemStatusHistory, UUID> {

    /**
     * Get all status changes for an item, newest first.
     * Used by: item history page, order detail item audit section
     */
    Page<ItemStatusHistory> findByOrderItemIdOrderByTimestampDesc(UUID orderItemId, Pageable pageable);

    /**
     * Get status changes made by a specific worker for an item.
     * Used by: worker self-service history view
     */
    Page<ItemStatusHistory> findByOrderItemIdAndUpdatedByOrderByTimestampDesc(
            UUID orderItemId, UUID updatedBy, Pageable pageable);
}