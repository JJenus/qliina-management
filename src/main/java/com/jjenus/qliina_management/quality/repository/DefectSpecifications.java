package com.jjenus.qliina_management.quality.repository;

import com.jjenus.qliina_management.quality.model.Defect;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DefectSpecifications {
    
    public static Specification<Defect> withFilters(
            UUID businessId, String status, String severity,
            LocalDateTime fromDate, LocalDateTime toDate, UUID assignedTo) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            
            predicates.add(cb.equal(root.get("qualityCheck").get("businessId"), businessId));
            
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            
            if (severity != null) {
                predicates.add(cb.equal(root.get("severity"), severity));
            }
            
            if (fromDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("reportedAt"), fromDate));
            }
            
            if (toDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("reportedAt"), toDate));
            }
            
            if (assignedTo != null) {
                predicates.add(cb.equal(root.get("assignedTo"), assignedTo));
            }
            
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}