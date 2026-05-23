package com.jjenus.qliina_management.expense.model;

import com.jjenus.qliina_management.common.BaseTenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "expenses", indexes = {
    @Index(name = "idx_expense_business_date", columnList = "business_id, expense_date"),
    @Index(name = "idx_expense_business_category", columnList = "business_id, category")
})
@Getter
@Setter
public class Expense extends BaseTenantEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 30)
    private ExpenseCategory category;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "expense_date", nullable = false)
    private LocalDate expenseDate;

    @Column(name = "paid_to")
    private String paidTo;

    /** Receipt / invoice reference number */
    @Column(name = "reference")
    private String reference;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_by")
    private java.util.UUID createdBy;
}
