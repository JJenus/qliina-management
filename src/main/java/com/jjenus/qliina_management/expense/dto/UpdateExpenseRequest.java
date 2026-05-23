package com.jjenus.qliina_management.expense.dto;

import com.jjenus.qliina_management.expense.model.ExpenseCategory;
import jakarta.validation.constraints.DecimalMin;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
public class UpdateExpenseRequest {
    private ExpenseCategory category;
    private String description;

    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    private BigDecimal amount;

    private LocalDate expenseDate;
    private String paidTo;
    private String reference;
    private String notes;
    private UUID shopId;
}
