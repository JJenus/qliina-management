package com.jjenus.qliina_management.expense.repository;

import com.jjenus.qliina_management.expense.model.Expense;
import com.jjenus.qliina_management.expense.model.ExpenseCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ExpenseRepository extends JpaRepository<Expense, UUID>, JpaSpecificationExecutor<Expense> {

    List<Expense> findByBusinessIdAndExpenseDateBetweenOrderByExpenseDateDesc(
            UUID businessId, LocalDate start, LocalDate end);

    List<Expense> findByBusinessIdAndCategoryAndExpenseDateBetween(
            UUID businessId, ExpenseCategory category, LocalDate start, LocalDate end);

    @Query("""
        SELECT e.category, SUM(e.amount)
        FROM Expense e
        WHERE e.businessId = :businessId
          AND e.expenseDate >= :start
          AND e.expenseDate <= :end
        GROUP BY e.category
        ORDER BY SUM(e.amount) DESC
    """)
    List<Object[]> sumByCategory(
            @Param("businessId") UUID businessId,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end);

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM Expense e WHERE e.businessId = :businessId AND e.expenseDate >= :start AND e.expenseDate <= :end")
    BigDecimal sumTotalByPeriod(
            @Param("businessId") UUID businessId,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end);
}
