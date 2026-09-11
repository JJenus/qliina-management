package com.jjenus.qliina_management.order.repository;

import com.jjenus.qliina_management.order.model.ItemStatusHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
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

    /**
     * Lightweight projection of a worker's work events used to compute
     * efficiency metrics (start → complete durations) and daily buckets.
     * Property names must match the query result aliases (itemId, ts).
     */
    interface WorkerEventProjection {
        UUID getItemId();
        LocalDateTime getTs();
    }

    /**
     * All "started work" events by a worker in a window.
     * Notes are written by WorkerOrderService as "ROLE started work (...)".
     */
    @Query("""
            SELECT h.orderItem.id AS itemId, h.timestamp AS ts
            FROM ItemStatusHistory h
            WHERE h.updatedBy = :workerId
              AND h.timestamp BETWEEN :start AND :end
              AND h.notes LIKE '%started work%'
            """)
    List<WorkerEventProjection> findWorkStarts(
            @Param("workerId") UUID workerId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    /**
     * All "completed work" events by a worker in a window.
     * Notes are written by WorkerOrderService as "ROLE completed work (...)".
     */
    @Query("""
            SELECT h.orderItem.id AS itemId, h.timestamp AS ts
            FROM ItemStatusHistory h
            WHERE h.updatedBy = :workerId
              AND h.timestamp BETWEEN :start AND :end
              AND h.notes LIKE '%completed work%'
            """)
    List<WorkerEventProjection> findWorkCompletions(
            @Param("workerId") UUID workerId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);
}