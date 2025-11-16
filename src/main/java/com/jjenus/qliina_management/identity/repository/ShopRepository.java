package com.jjenus.qliina_management.identity.repository;

import com.jjenus.qliina_management.identity.model.Shop;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

@Repository
public interface ShopRepository extends JpaRepository<Shop, UUID> {
    Optional<Shop> findByCode(String code);
    
    @Query("SELECT s FROM Shop s WHERE s.businessId = :businessId")
    Page<Shop> findByBusinessId(@Param("businessId") UUID businessId, Pageable pageable);
    
    @Query("SELECT s FROM Shop s WHERE s.businessId = :businessId AND s.active = true")
    List<Shop> findActiveByBusinessId(@Param("businessId") UUID businessId);
    
    Boolean existsByCode(String code);
    
    @Query("SELECT s.id FROM Shop s WHERE s.businessId = :businessId AND s.active = true")
List<UUID> findActiveShopIdsByBusinessId(@Param("businessId") UUID businessId);
}
