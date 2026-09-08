package com.jjenus.qliina_management.order.repository;

import com.jjenus.qliina_management.customer.model.Customer;
import com.jjenus.qliina_management.order.dto.OrderFilter;
import com.jjenus.qliina_management.order.model.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.math.BigDecimal;

public class OrderSpecifications {
    
    public static Specification<Order> withFilter(UUID businessId, OrderFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            
            predicates.add(cb.equal(root.get("businessId"), businessId));
            
            if (filter == null) {
                return cb.and(predicates.toArray(new Predicate[0]));
            }
            
            if (filter.getShopId() != null) {
                predicates.add(cb.equal(root.get("shopId"), filter.getShopId()));
            }
            
            if (filter.getCustomerId() != null) {
                predicates.add(cb.equal(root.get("customerId"), filter.getCustomerId()));
            }
            
            if (filter.getStatus() != null && !filter.getStatus().isEmpty()) {
                predicates.add(root.get("status").in(filter.getStatus()));
            }
            
            if (filter.getFromDate() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), filter.getFromDate()));
            }
            
            if (filter.getToDate() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), filter.getToDate()));
            }
            
            if (filter.getMinAmount() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("totalAmount"), filter.getMinAmount()));
            }
            
            if (filter.getMaxAmount() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("totalAmount"), filter.getMaxAmount()));
            }
            
            if (filter.getPaymentStatus() != null) {
                switch (filter.getPaymentStatus()) {
                    case "PAID":
                        predicates.add(cb.equal(root.get("balanceDue"), BigDecimal.ZERO));
                        break;
                    case "UNPAID":
                        predicates.add(cb.equal(root.get("paidAmount"), BigDecimal.ZERO));
                        break;
                    case "PARTIAL":
                        predicates.add(cb.greaterThan(root.get("paidAmount"), BigDecimal.ZERO));
                        predicates.add(cb.greaterThan(root.get("balanceDue"), BigDecimal.ZERO));
                        break;
                }
            }
            
            if (filter.getPriority() != null) {
                predicates.add(cb.equal(root.get("priority"), Order.Priority.valueOf(filter.getPriority())));
            }
            
            if (filter.getTag() != null) {
                predicates.add(cb.isMember(filter.getTag(), root.get("tags")));
            }
            
            if (filter.getHasIssue() != null && filter.getHasIssue()) {
                predicates.add(cb.isNotEmpty(root.get("items").get("defects")));
            }
            
            if (filter.getSearch() != null && !filter.getSearch().isBlank()) {
                String term = "%" + filter.getSearch().trim().toLowerCase() + "%";
                
                Subquery<UUID> matchingCustomers = query.subquery(UUID.class);
                Root<Customer> customer = matchingCustomers.from(Customer.class);
                matchingCustomers.select(customer.get("id")).where(
                    cb.and(
                        cb.equal(customer.get("businessId"), businessId),
                        cb.or(
                            cb.like(cb.lower(customer.get("firstName")), term),
                            cb.like(cb.lower(customer.get("lastName")), term),
                            cb.like(cb.lower(
                                cb.concat(customer.get("firstName"),
                                    cb.concat(" ", customer.get("lastName")))), term),
                            cb.like(cb.lower(customer.get("phone")), term)
                        )
                    ));
                
                predicates.add(cb.or(
                    cb.like(cb.lower(root.get("trackingNumber")), term),
                    cb.like(cb.lower(root.get("orderNumber")), term),
                    root.get("customerId").in(matchingCustomers)
                ));
            }
            
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
