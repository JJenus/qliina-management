package com.jjenus.qliina_management.audit.repository;

import com.jjenus.qliina_management.audit.model.AuditLog;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * JPA Specifications for dynamic AuditLog queries.
 *
 * Replaces the problematic JPQL @Query that failed type-inference when
 * a typed enum parameter (AuditSeverity) was passed as null. Each criterion
 * is added only when the caller supplies a non-null, non-blank value, making
 * all filters genuinely optional without JPQL null-comparison hacks.
 */
public final class AuditLogSpecifications {

    private AuditLogSpecifications() {}

    public static Specification<AuditLog> search(
            UUID businessId,
            UUID userId,
            String entityType,
            UUID entityId,
            String action,
            String category,
            AuditLog.AuditSeverity severity,
            LocalDateTime fromDate,
            LocalDateTime toDate,
            String ipAddress) {

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // businessId is mandatory
            predicates.add(cb.equal(root.get("businessId"), businessId));

            if (userId != null)
                predicates.add(cb.equal(root.get("userId"), userId));

            if (entityType != null && !entityType.isBlank())
                predicates.add(cb.equal(root.get("entityType"), entityType));

            if (entityId != null)
                predicates.add(cb.equal(root.get("entityId"), entityId));

            if (action != null && !action.isBlank())
                predicates.add(cb.like(root.get("action"), "%" + action + "%"));

            if (category != null && !category.isBlank())
                predicates.add(cb.equal(root.get("category"), category));

            if (severity != null)
                predicates.add(cb.equal(root.get("severity"), severity));

            if (fromDate != null)
                predicates.add(cb.greaterThanOrEqualTo(root.get("timestamp"), fromDate));

            if (toDate != null)
                predicates.add(cb.lessThanOrEqualTo(root.get("timestamp"), toDate));

            if (ipAddress != null && !ipAddress.isBlank())
                predicates.add(cb.equal(root.get("ipAddress"), ipAddress));

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
