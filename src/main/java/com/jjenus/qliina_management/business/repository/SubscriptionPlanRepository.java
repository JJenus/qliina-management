package com.jjenus.qliina_management.business.repository;

import com.jjenus.qliina_management.business.model.SubscriptionPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, UUID> {

    Optional<SubscriptionPlan> findByTier(String tier);

    List<SubscriptionPlan> findAllByIsActiveTrue();

    boolean existsByTier(String tier);
}
