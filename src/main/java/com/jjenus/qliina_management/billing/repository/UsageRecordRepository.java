package com.jjenus.qliina_management.billing.repository;

import com.jjenus.qliina_management.billing.model.UsageRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Repository
public interface UsageRecordRepository extends JpaRepository<UsageRecord, UUID> {

    /**
     * Total metered usage for a feature within a period — feeds metered billing.
     */
    @Query("SELECT COALESCE(SUM(u.quantity), 0) FROM UsageRecord u " +
            "WHERE u.subscriptionId = :subscriptionId AND u.featureKey = :featureKey " +
            "AND u.recordedAt >= :from AND u.recordedAt < :to")
    BigDecimal sumUsage(@Param("subscriptionId") UUID subscriptionId,
                        @Param("featureKey") String featureKey,
                        @Param("from") LocalDateTime from,
                        @Param("to") LocalDateTime to);
}
