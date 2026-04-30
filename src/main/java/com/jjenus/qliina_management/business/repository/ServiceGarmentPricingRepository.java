package com.jjenus.qliina_management.business.repository;

import com.jjenus.qliina_management.business.model.ServiceGarmentPricing;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ServiceGarmentPricingRepository extends JpaRepository<ServiceGarmentPricing, UUID> {

    List<ServiceGarmentPricing> findByBusinessIdAndServiceTypeIdAndIsActiveTrue(
            UUID businessId, UUID serviceTypeId);

    List<ServiceGarmentPricing> findByBusinessIdAndIsActiveTrue(UUID businessId);

    Optional<ServiceGarmentPricing> findByServiceTypeIdAndGarmentTypeIdAndIsActiveTrue(
            UUID serviceTypeId, UUID garmentTypeId);

    @Query("SELECT sgp FROM ServiceGarmentPricing sgp " +
           "WHERE sgp.businessId = :businessId AND sgp.isActive = true " +
           "ORDER BY sgp.serviceTypeId, sgp.garmentTypeId")
    List<ServiceGarmentPricing> findActivePricingGrid(@Param("businessId") UUID businessId);
}