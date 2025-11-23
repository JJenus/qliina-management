package com.jjenus.qliina_management.audit.model;

import com.jjenus.qliina_management.audit.dto.AuditLogFilter;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class AuditLogSpecification {
    
    public static Specification<AuditLog> withFilter(UUID businessId, AuditLogFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            
            predicates.add(cb.equal(root.get("businessId"), businessId));
            
            if (filter == null) {
                return cb.and(predicates.toArray(new Predicate[0]));
            }
            
            if (filter.getUserId() != null) {
                predicates.add(cb.equal(root.get("userId"), filter.getUserId()));
            }
            
            if (filter.getEntityType() != null) {
                predicates.add(cb.equal(root.get("entityType"), filter.getEntityType()));
            }
            
            if (filter.getEntityId() != null) {
                predicates.add(cb.equal(root.get("entityId"), filter.getEntityId()));
            }
            
            if (filter.getAction() != null) {
                predicates.add(cb.like(root.get("action"), "%" + filter.getAction() + "%"));
            }
            
            if (filter.getCategory() != null) {
                predicates.add(cb.equal(root.get("category"), filter.getCategory()));
            }
            
            if (filter.getSeverity() != null) {
                predicates.add(cb.equal(root.get("severity"), 
                    AuditLog.AuditSeverity.valueOf(filter.getSeverity())));
            }
            
            if (filter.getFromDate() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("timestamp"), filter.getFromDate()));
            }
            
            if (filter.getToDate() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("timestamp"), filter.getToDate()));
            }
            
            if (filter.getIpAddress() != null) {
                predicates.add(cb.equal(root.get("ipAddress"), filter.getIpAddress()));
            }
            
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}