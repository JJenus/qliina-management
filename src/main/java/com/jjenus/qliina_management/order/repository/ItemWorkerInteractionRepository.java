package com.jjenus.qliina_management.order.repository;

import com.jjenus.qliina_management.order.model.ItemWorkerInteraction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ItemWorkerInteractionRepository extends JpaRepository<ItemWorkerInteraction, UUID> {

    /**
     * Check if a worker has ever interacted with a specific item.
     */
    Optional<ItemWorkerInteraction> findByWorkerIdAndItemId(UUID workerId, UUID itemId);

    /**
     * Get all items a worker has interacted with, ordered by most recent.
     */
    @Query("SELECT iwi FROM ItemWorkerInteraction iwi " +
           "WHERE iwi.workerId = :workerId " +
           "ORDER BY iwi.lastInteraction DESC")
    Page<ItemWorkerInteraction> findByWorkerId(@Param("workerId") UUID workerId, Pageable pageable);

    /**
     * Get all items a worker has interacted with in a specific business context.
     */
    @Query("SELECT iwi FROM ItemWorkerInteraction iwi " +
           "JOIN OrderItem oi ON iwi.itemId = oi.id " +
           "JOIN oi.order o " +
           "WHERE iwi.workerId = :workerId AND o.businessId = :businessId " +
           "ORDER BY iwi.lastInteraction DESC")
    Page<ItemWorkerInteraction> findByWorkerIdAndBusinessId(
            @Param("workerId") UUID workerId,
            @Param("businessId") UUID businessId,
            Pageable pageable);

    /**
     * Get all workers who have interacted with a specific item.
     */
    @Query("SELECT iwi FROM ItemWorkerInteraction iwi " +
           "WHERE iwi.itemId = :itemId " +
           "ORDER BY iwi.lastInteraction DESC")
    List<ItemWorkerInteraction> findByItemId(@Param("itemId") UUID itemId);

    /**
     * Batch check: which of these items has this worker interacted with?
     */
    @Query("SELECT iwi.itemId FROM ItemWorkerInteraction iwi " +
           "WHERE iwi.workerId = :workerId AND iwi.itemId IN :itemIds")
    List<UUID> findInteractedItemIds(@Param("workerId") UUID workerId,
                                      @Param("itemIds") List<UUID> itemIds);

    /**
     * Count interactions for a worker in a date range.
     */
    @Query("SELECT COUNT(iwi) FROM ItemWorkerInteraction iwi " +
           "WHERE iwi.workerId = :workerId " +
           "AND iwi.lastInteraction BETWEEN :start AND :end")
    long countInteractionsByWorkerAndDateRange(
            @Param("workerId") UUID workerId,
            @Param("start") java.time.LocalDateTime start,
            @Param("end") java.time.LocalDateTime end);
}