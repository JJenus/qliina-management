package com.jjenus.qliina_management.billing.repository;

import com.jjenus.qliina_management.billing.model.PlanFeature;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PlanFeatureRepository extends JpaRepository<PlanFeature, UUID> {

    List<PlanFeature> findAllByPlanId(UUID planId);
}
