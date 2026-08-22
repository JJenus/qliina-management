package com.jjenus.qliina_management.notification.repository;

import com.jjenus.qliina_management.notification.model.NotificationDelivery;
import com.jjenus.qliina_management.notification.model.NotificationDeliveryStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationDeliveryRepository extends JpaRepository<NotificationDelivery, UUID> {

    @Query("SELECT d.status, COUNT(d) FROM NotificationDelivery d GROUP BY d.status")
    List<Object[]> countGroupedByStatus();

    List<NotificationDelivery> findByNotificationIdOrderByCreatedAtAsc(UUID notificationId);

    long countByNotificationIdAndStatus(UUID notificationId, NotificationDeliveryStatus status);

    /** Re-lock a single delivery while dispatching — serializes sweep + outbox worker races. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM NotificationDelivery d WHERE d.id = :id")
    Optional<NotificationDelivery> findByIdForUpdate(@Param("id") UUID id);

    /**
     * Dispatch sweep: never-yet-dispatched deliveries. Row locking comes from the
     * SQL {@code FOR UPDATE SKIP LOCKED} — @Lock is deliberately NOT applied
     * because Spring Data cannot set a JPA lock mode on native queries.
     */
    @Query(value = "SELECT * FROM notification_deliveries WHERE status = 'PENDING' " +
            "AND (next_retry_at IS NULL OR next_retry_at <= :now) " +
            "ORDER BY created_at FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<NotificationDelivery> lockDueForDispatch(@Param("now") LocalDateTime now);

    /**
     * Retry sweep (doc §4): failed deliveries whose backoff window has elapsed and
     * that still have attempts left on their budget.
     */
    @Query(value = "SELECT * FROM notification_deliveries WHERE status = 'FAILED' " +
            "AND attempt_count < max_attempts " +
            "AND next_retry_at IS NOT NULL AND next_retry_at <= :now " +
            "ORDER BY next_retry_at FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<NotificationDelivery> lockDueForRetry(@Param("now") LocalDateTime now);
}