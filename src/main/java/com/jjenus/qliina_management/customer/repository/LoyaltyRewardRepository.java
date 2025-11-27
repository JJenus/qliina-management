
package com.jjenus.qliina_management.customer.repository;

import com.jjenus.qliina_management.customer.model.LoyaltyReward;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface LoyaltyRewardRepository extends JpaRepository<LoyaltyReward, UUID> {
    
    @Query("SELECT lr FROM LoyaltyReward lr WHERE lr.businessId = :businessId AND lr.isActive = true AND (lr.expiresAt IS NULL OR lr.expiresAt > :now)")
    List<LoyaltyReward> findAvailableRewards(@Param("businessId") UUID businessId, @Param("now") LocalDateTime now);
}