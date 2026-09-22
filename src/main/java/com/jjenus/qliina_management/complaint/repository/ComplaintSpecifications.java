package com.jjenus.qliina_management.complaint.repository;

import com.jjenus.qliina_management.complaint.model.Complaint;
import com.jjenus.qliina_management.complaint.model.ComplaintSeverity;
import com.jjenus.qliina_management.complaint.model.ComplaintStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ComplaintSpecifications {

    /** Tenant-scoped filter — businessId is always enforced. */
    public static Specification<Complaint> tenantFilter(
            UUID businessId, ComplaintStatus status, ComplaintSeverity severity) {

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("businessId"), businessId));
            if (status != null)
                predicates.add(cb.equal(root.get("status"), status));
            if (severity != null)
                predicates.add(cb.equal(root.get("severity"), severity));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /** Platform-scoped filter — businessId is optional (cross-tenant inbox). */
    public static Specification<Complaint> platformFilter(
            UUID businessId, ComplaintStatus status, ComplaintSeverity severity) {

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (businessId != null)
                predicates.add(cb.equal(root.get("businessId"), businessId));
            if (status != null)
                predicates.add(cb.equal(root.get("status"), status));
            if (severity != null)
                predicates.add(cb.equal(root.get("severity"), severity));
            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}