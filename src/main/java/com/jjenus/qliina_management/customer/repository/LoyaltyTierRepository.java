
package com.jjenus.qliina_management.customer.repository;

import com.jjenus.qliina_management.customer.model.LoyaltyTier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LoyaltyTierRepository extends JpaRepository<LoyaltyTier, UUID> {
    
    List<LoyaltyTier> findByBusinessIdOrderByLevelAsc(UUID businessId);
    
    @Query("SELECT lt FROM LoyaltyTier lt WHERE lt.businessId = :businessId AND lt.pointsRequired <= :points ORDER BY lt.level ASC")
    List<LoyaltyTier> findEligibleTiers(@Param("businessId") UUID businessId, @Param("points") Integer points);
}