package com.jjenus.qliina_management.order.repository;

import com.jjenus.qliina_management.order.model.OrderItemUnit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderItemUnitRepository extends JpaRepository<OrderItemUnit, UUID> {

    /**
     * Finds a unit by its full barcode (e.g. "QL-FX78GLJ6-01") scoped to a business.
     * Scoping by businessId prevents cross-business barcode collisions.
     */
    @Query("""
        SELECT u FROM OrderItemUnit u
        WHERE u.orderItem.order.businessId = :businessId
          AND u.barcode = :barcode
        """)
    Optional<OrderItemUnit> findByBusinessIdAndBarcode(
            @Param("businessId") UUID businessId,
            @Param("barcode")    String barcode);
}
