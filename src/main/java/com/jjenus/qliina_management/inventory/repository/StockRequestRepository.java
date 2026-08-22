// ./src/main/java/com/jjenus/qliina_management/inventory/repository/StockRequestRepository.java
package com.jjenus.qliina_management.inventory.repository;

import com.jjenus.qliina_management.inventory.model.StockRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface StockRequestRepository extends JpaRepository<StockRequest, UUID> {

    Page<StockRequest> findByBusinessIdAndStatus(UUID businessId, StockRequest.RequestStatus status, Pageable pageable);

    Page<StockRequest> findByBusinessId(UUID businessId, Pageable pageable);

    Page<StockRequest> findByBusinessIdAndRequestedBy(
            UUID businessId, UUID requestedBy, Pageable pageable);
}
