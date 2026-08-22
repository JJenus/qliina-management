package com.jjenus.qliina_management.billing.repository;

import com.jjenus.qliina_management.billing.model.SubscriptionEvent;
import com.jjenus.qliina_management.billing.model.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface SubscriptionEventRepository extends JpaRepository<SubscriptionEvent, UUID> {

    List<SubscriptionEvent> findAllBySubscriptionIdOrderByOccurredAtDesc(UUID subscriptionId);

    /** Trial → paid conversions within a window (growth funnel). */
    long countByFromStatusAndToStatusAndOccurredAtAfter(
            SubscriptionStatus from, SubscriptionStatus to, LocalDateTime since);

    /** Events landing in a given status within a window. */
    long countByToStatusAndOccurredAtAfter(SubscriptionStatus to, LocalDateTime since);
}
