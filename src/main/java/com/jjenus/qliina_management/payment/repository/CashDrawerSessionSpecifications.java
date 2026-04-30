package com.jjenus.qliina_management.payment.repository;

import com.jjenus.qliina_management.payment.model.CashDrawerSession;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class CashDrawerSessionSpecifications {
    
    public static Specification<CashDrawerSession> withFilters(
            UUID businessId, UUID shopId, String status,
            LocalDateTime fromDate, LocalDateTime toDate) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            
            predicates.add(cb.equal(root.get("businessId"), businessId));
            
            if (shopId != null) {
                predicates.add(cb.equal(root.get("shopId"), shopId));
            }
            
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            
            if (fromDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("openedAt"), fromDate));
            }
            
            if (toDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("openedAt"), toDate));
            }
            
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}