package com.jjenus.qliina_management.business.repository;

import com.jjenus.qliina_management.business.model.GarmentType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface GarmentTypeRepository extends JpaRepository<GarmentType, UUID> {

    List<GarmentType> findByBusinessIdAndIsActiveTrueOrderBySortOrderAsc(UUID businessId);

    Page<GarmentType> findByBusinessId(UUID businessId, Pageable pageable);

    boolean existsByBusinessIdAndNameIgnoreCase(UUID businessId, String name);
}