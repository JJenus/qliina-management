package com.jjenus.qliina_management.billing.repository;

import com.jjenus.qliina_management.billing.model.Subscription;
import com.jjenus.qliina_management.billing.model.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    /** The live (non-terminal) subscription for a business. */
    Optional<Subscription> findByBusinessIdAndStatusNot(UUID businessId, SubscriptionStatus status);

    /** The most recent subscription row for a business (for history/audit). */
    Optional<Subscription> findTopByBusinessIdOrderByCreatedAtDesc(UUID businessId);

    Optional<Subscription> findByBusinessIdAndStatus(UUID businessId, SubscriptionStatus status);

    /**
     * Re-lock a single subscription for the duration of its renewal/retry —
     * serializes concurrent job sweeps on the same row.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Subscription s WHERE s.id = :id")
    Optional<Subscription> findByIdForUpdate(@Param("id") UUID id);

    /**
     * Cancellations scheduled at period end that have now lapsed.
     * Row locking comes from the SQL {@code FOR UPDATE SKIP LOCKED} — {@code @Lock}
     * is deliberately NOT applied because Spring Data cannot set a JPA lock mode
     * on native queries.
     */
    @Query(value = "SELECT * FROM billing_subscriptions WHERE status = 'ACTIVE' " +
            "AND cancel_at_period_end = TRUE " +
            "AND current_period_end IS NOT NULL AND current_period_end <= :now " +
            "ORDER BY current_period_end FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<Subscription> lockPendingCancellations(@Param("now") LocalDateTime now);

    /**
     * Multi-instance-safe renewal sweep: locks the rows so no two app instances
     * bill the same subscription (§5 fault tolerance).
     */
    @Query(value = "SELECT * FROM billing_subscriptions WHERE status = 'ACTIVE' " +
            "AND cancel_at_period_end = FALSE " +
            "AND current_period_end IS NOT NULL AND current_period_end <= :now " +
            "ORDER BY current_period_end FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<Subscription> lockDueForRenewal(@Param("now") LocalDateTime now);

    /** Dunning sweep — past_due subscriptions whose next retry is due. */
    @Query(value = "SELECT * FROM billing_subscriptions WHERE status = 'PAST_DUE' " +
            "AND next_retry_at IS NOT NULL AND next_retry_at <= :now " +
            "ORDER BY next_retry_at FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<Subscription> lockDueForRetry(@Param("now") LocalDateTime now);

    /** Trial-expiry sweep — trialing subscriptions past their trial end. */
    @Query(value = "SELECT * FROM billing_subscriptions WHERE status = 'TRIALING' " +
            "AND trial_ends_at IS NOT NULL AND trial_ends_at <= :now " +
            "ORDER BY trial_ends_at FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<Subscription> lockExpiredTrials(@Param("now") LocalDateTime now);
}
