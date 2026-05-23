package com.jjenus.qliina_management.inventory.repository;

import com.jjenus.qliina_management.inventory.model.PurchaseOrder;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * JPA Specifications for PurchaseOrder filtering.
 *
 * Replaces the JPQL @Query with nullable enum parameter which triggers
 * "could not determine data type of parameter" on PostgreSQL.
 */
public class PurchaseOrderSpecifications {

    private PurchaseOrderSpecifications() {}

    public static Specification<PurchaseOrder> search(
            UUID businessId,
            UUID supplierId,
            UUID shopId,
            PurchaseOrder.PurchaseOrderStatus status,
            LocalDateTime fromDate,
            LocalDateTime toDate) {

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // businessId is always required
            predicates.add(cb.equal(root.get("businessId"), businessId));

            if (supplierId != null) {
                predicates.add(cb.equal(root.get("supplier").get("id"), supplierId));
            }
            if (shopId != null) {
                predicates.add(cb.equal(root.get("shopId"), shopId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (fromDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("orderDate"), fromDate));
            }
            if (toDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("orderDate"), toDate));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
