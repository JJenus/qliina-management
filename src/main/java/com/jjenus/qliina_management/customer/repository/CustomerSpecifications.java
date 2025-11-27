package com.jjenus.qliina_management.customer.repository;

import com.jjenus.qliina_management.customer.model.Customer;
import com.jjenus.qliina_management.customer.dto.CustomerFilter;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class CustomerSpecifications {
    
    public static Specification<Customer> withFilter(UUID businessId, CustomerFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            
            predicates.add(cb.equal(root.get("businessId"), businessId));
            
            if (filter == null) {
                return cb.and(predicates.toArray(new Predicate[0]));
            }
            
            if (filter.getMinSpend() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("totalSpent"), filter.getMinSpend()));
            }
            
            if (filter.getMaxSpend() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("totalSpent"), filter.getMaxSpend()));
            }
            
            if (filter.getMinOrders() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("totalOrders"), filter.getMinOrders()));
            }
            
            if (filter.getLastOrderFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("lastOrderDate"), filter.getLastOrderFrom()));
            }
            
            if (filter.getLastOrderTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("lastOrderDate"), filter.getLastOrderTo()));
            }
            
            if (filter.getLoyaltyTier() != null && !filter.getLoyaltyTier().isEmpty()) {
                predicates.add(cb.equal(root.get("loyaltyTier"), filter.getLoyaltyTier()));
            }
            
            if (filter.getTags() != null && !filter.getTags().isEmpty()) {
                for (String tag : filter.getTags()) {
                    predicates.add(cb.isMember(tag, root.get("tags")));
                }
            }
            
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
