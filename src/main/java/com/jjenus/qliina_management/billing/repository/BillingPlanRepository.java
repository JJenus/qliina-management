package com.jjenus.qliina_management.billing.repository;

import com.jjenus.qliina_management.billing.model.BillingPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BillingPlanRepository extends JpaRepository<BillingPlan, UUID> {

    List<BillingPlan> findAllByStatusOrderByNameAsc(com.jjenus.qliina_management.billing.model.PlanStatus status);

    boolean existsByName(String name);
}
