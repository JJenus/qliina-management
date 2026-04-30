package com.jjenus.qliina_management.notification.repository;

import com.jjenus.qliina_management.notification.model.Notification;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class NotificationSpecifications {
    
    public static Specification<Notification> withFilters(
            UUID businessId, UUID userId, String type, String status,
            LocalDateTime fromDate, LocalDateTime toDate) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            
            predicates.add(cb.equal(root.get("businessId"), businessId));
            
            if (userId != null) {
                predicates.add(cb.equal(root.get("userId"), userId));
            }
            
            if (type != null) {
                predicates.add(cb.equal(root.get("type"), Notification.NotificationType.valueOf(type)));
            }
            
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), Notification.NotificationStatus.valueOf(status)));
            }
            
            if (fromDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), fromDate));
            }
            
            if (toDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), toDate));
            }
            
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}