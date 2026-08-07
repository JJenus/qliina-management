package com.jjenus.qliina_management.billing.repository;

import com.jjenus.qliina_management.billing.model.BillingPlan;
import com.jjenus.qliina_management.billing.model.PlanVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PlanVersionRepository extends JpaRepository<PlanVersion, UUID> {

    /** The version that was effective at {@code at} — used when no explicit version is given. */
    Optional<PlanVersion> findFirstByPlanIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
            UUID planId, java.time.LocalDateTime at);

    Optional<PlanVersion> findFirstByPlanOrderByEffectiveFromDesc(BillingPlan plan);

    List<PlanVersion> findAllByPlanIdOrderByEffectiveFromAsc(UUID planId);
}
