package com.jjenus.qliina_management.billing.repository;

import com.jjenus.qliina_management.billing.model.SubscriptionEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SubscriptionEventRepository extends JpaRepository<SubscriptionEvent, UUID> {

    List<SubscriptionEvent> findAllBySubscriptionIdOrderByOccurredAtDesc(UUID subscriptionId);
}
