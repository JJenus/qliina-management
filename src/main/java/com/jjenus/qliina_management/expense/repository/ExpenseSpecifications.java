package com.jjenus.qliina_management.expense.repository;

import com.jjenus.qliina_management.expense.model.Expense;
import com.jjenus.qliina_management.expense.model.ExpenseCategory;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ExpenseSpecifications {

    public static Specification<Expense> withFilter(
            UUID businessId, UUID shopId, ExpenseCategory category,
            LocalDate from, LocalDate to) {

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("businessId"), businessId));

            if (shopId != null)
                predicates.add(cb.equal(root.get("shopId"), shopId));
            if (category != null)
                predicates.add(cb.equal(root.get("category"), category));
            if (from != null)
                predicates.add(cb.greaterThanOrEqualTo(root.get("expenseDate"), from));
            if (to != null)
                predicates.add(cb.lessThanOrEqualTo(root.get("expenseDate"), to));

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
