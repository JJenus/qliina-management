package com.jjenus.qliina_management.inventory.repository;

import com.jjenus.qliina_management.inventory.model.StockTransaction;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class StockTransactionSpecifications {
    
    public static Specification<StockTransaction> withFilters(
            UUID businessId, UUID shopId, UUID itemId, 
            StockTransaction.TransactionType type,
            LocalDateTime fromDate, LocalDateTime toDate) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            
            predicates.add(cb.equal(root.get("businessId"), businessId));
            
            if (shopId != null) {
                predicates.add(cb.equal(root.get("shopId"), shopId));
            }
            
            if (itemId != null) {
                predicates.add(cb.equal(root.get("item").get("id"), itemId));
            }
            
            if (type != null) {
                predicates.add(cb.equal(root.get("type"), type));
            }
            
            if (fromDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("transactionDate"), fromDate));
            }
            
            if (toDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("transactionDate"), toDate));
            }
            
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}