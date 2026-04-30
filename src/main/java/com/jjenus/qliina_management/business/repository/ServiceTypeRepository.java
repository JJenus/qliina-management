package com.jjenus.qliina_management.business.repository;

import com.jjenus.qliina_management.business.model.ServiceType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ServiceTypeRepository extends JpaRepository<ServiceType, UUID> {

    List<ServiceType> findByBusinessIdAndIsActiveTrueOrderBySortOrderAsc(UUID businessId);

    Page<ServiceType> findByBusinessId(UUID businessId, Pageable pageable);

    @Query("SELECT st FROM ServiceType st WHERE st.businessId = :businessId " +
           "AND (LOWER(st.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(st.category) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "AND st.isActive = true")
    Page<ServiceType> searchActive(@Param("businessId") UUID businessId,
                                    @Param("search") String search,
                                    Pageable pageable);

    boolean existsByBusinessIdAndNameIgnoreCase(UUID businessId, String name);
}