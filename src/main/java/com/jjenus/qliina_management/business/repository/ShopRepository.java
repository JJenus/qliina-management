package com.jjenus.qliina_management.business.repository;

import com.jjenus.qliina_management.business.model.Shop;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Shop entities.
 *
 * Module note: moved from identity to business. The identity package retains
 * a compatibility stub (identity/repository/ShopRepository.java) so existing
 * callers compile without import changes.
 */
@Repository
public interface ShopRepository extends JpaRepository<Shop, UUID> {

    Optional<Shop> findByCode(String code);

    @Query("SELECT s FROM Shop s WHERE s.businessId = :businessId")
    Page<Shop> findByBusinessId(@Param("businessId") UUID businessId, Pageable pageable);

    @Query("SELECT s FROM Shop s WHERE s.businessId = :businessId AND s.active = true")
    List<Shop> findActiveByBusinessId(@Param("businessId") UUID businessId);

    @Query("SELECT s.id FROM Shop s WHERE s.businessId = :businessId AND s.active = true")
    List<UUID> findActiveShopIdsByBusinessId(@Param("businessId") UUID businessId);

    boolean existsByCode(String code);

    /** Count of active shops — used for plan-limit enforcement and BusinessDTO. */
    @Query("SELECT COUNT(s) FROM Shop s WHERE s.businessId = :businessId AND s.active = true")
    long countActiveByBusinessId(@Param("businessId") UUID businessId);
}
