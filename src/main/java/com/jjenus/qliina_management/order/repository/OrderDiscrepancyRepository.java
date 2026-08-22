// ./src/main/java/com/jjenus/qliina_management/order/repository/OrderDiscrepancyRepository.java
package com.jjenus.qliina_management.order.repository;

import com.jjenus.qliina_management.order.model.OrderDiscrepancy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface OrderDiscrepancyRepository extends JpaRepository<OrderDiscrepancy, UUID> {

    Page<OrderDiscrepancy> findByBusinessIdAndStatus(
            UUID businessId, OrderDiscrepancy.DiscrepancyStatus status, Pageable pageable);

    Page<OrderDiscrepancy> findByBusinessId(UUID businessId, Pageable pageable);

    Page<OrderDiscrepancy> findByBusinessIdAndReportedBy(
            UUID businessId, UUID reportedBy, Pageable pageable);

    long countByBusinessIdAndStatus(UUID businessId, OrderDiscrepancy.DiscrepancyStatus status);
}
