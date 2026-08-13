package com.jjenus.qliina_management.notification.repository;

import com.jjenus.qliina_management.notification.model.NotificationOutbox;
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
public interface NotificationOutboxRepository extends JpaRepository<NotificationOutbox, UUID> {

    /** Re-lock a single row before processing — prevents concurrent workers from both acting. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM NotificationOutbox o WHERE o.id = :id")
    Optional<NotificationOutbox> findByIdForUpdate(@Param("id") UUID id);

    /**
     * Claim job: rows that are due (not scheduled in the future), not rate-limited
     * by next_attempt_at, and whose processing lease has not expired. Row locking
     * comes from {@code FOR UPDATE SKIP LOCKED}; a crashed worker's lease
     * (locked_at older than the timeout) makes its row reclaimable — queue-level
     * redelivery (doc §5).
     */
    @Query(value = "SELECT * FROM notification_outbox WHERE status = 'PENDING' " +
            "AND (scheduled_for IS NULL OR scheduled_for <= :now) " +
            "AND (next_attempt_at IS NULL OR next_attempt_at <= :now) " +
            "AND (locked_at IS NULL OR locked_at < :leaseExpiry) " +
            "ORDER BY created_at FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<NotificationOutbox> lockDue(@Param("now") LocalDateTime now,
                                     @Param("leaseExpiry") LocalDateTime leaseExpiry);
}