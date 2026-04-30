package com.jjenus.qliina_management.payment.repository;

import com.jjenus.qliina_management.payment.dto.PaymentFilter;
import com.jjenus.qliina_management.payment.model.OrderPayment;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PaymentSpecifications {
    
    public static Specification<OrderPayment> withFilter(UUID businessId, PaymentFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            
            predicates.add(cb.equal(root.get("businessId"), businessId));
            
            if (filter == null) {
                return cb.and(predicates.toArray(new Predicate[0]));
            }
            
            if (filter.getOrderId() != null) {
                predicates.add(cb.equal(root.get("orderId"), filter.getOrderId()));
            }
            
            if (filter.getCustomerId() != null) {
                predicates.add(cb.equal(root.get("customerId"), filter.getCustomerId()));
            }
            
            if (filter.getShopId() != null) {
                predicates.add(cb.equal(root.get("shopId"), filter.getShopId()));
            }
            
            if (filter.getMethod() != null) {
                predicates.add(cb.equal(root.get("method"), filter.getMethod()));
            }
            
            if (filter.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), filter.getStatus()));
            }
            
            if (filter.getFromDate() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("paidAt"), filter.getFromDate()));
            }
            
            if (filter.getToDate() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("paidAt"), filter.getToDate()));
            }
            
            if (filter.getCollectedBy() != null) {
                predicates.add(cb.equal(root.get("collectedBy"), filter.getCollectedBy()));
            }
            
            if (filter.getMinAmount() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("amount"), filter.getMinAmount()));
            }
            
            if (filter.getMaxAmount() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("amount"), filter.getMaxAmount()));
            }
            
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}