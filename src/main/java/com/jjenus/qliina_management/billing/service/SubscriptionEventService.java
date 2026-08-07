package com.jjenus.qliina_management.billing.service;

import com.jjenus.qliina_management.billing.model.SubscriptionEvent;
import com.jjenus.qliina_management.billing.model.SubscriptionStatus;
import com.jjenus.qliina_management.billing.repository.SubscriptionEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Append-only transition ledger (§6). Every status change is recorded here —
 * this is what support and analytics query for churn, never the status column.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionEventService {

    private final SubscriptionEventRepository eventRepository;

    public void record(UUID subscriptionId, SubscriptionStatus from, SubscriptionStatus to, String reason) {
        eventRepository.save(SubscriptionEvent.builder()
                .subscriptionId(subscriptionId)
                .fromStatus(from)
                .toStatus(to)
                .reason(reason)
                .occurredAt(LocalDateTime.now())
                .build());
        log.info("Subscription {} {} -> {} ({})", subscriptionId, from, to, reason);
    }
}
