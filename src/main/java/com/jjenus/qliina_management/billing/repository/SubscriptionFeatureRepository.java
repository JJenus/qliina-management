package com.jjenus.qliina_management.billing.repository;

import com.jjenus.qliina_management.billing.model.SubscriptionFeature;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SubscriptionFeatureRepository extends JpaRepository<SubscriptionFeature, UUID> {

    List<SubscriptionFeature> findAllBySubscriptionId(UUID subscriptionId);
}
