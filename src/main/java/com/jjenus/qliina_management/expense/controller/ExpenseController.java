package com.jjenus.qliina_management.expense.controller;

import com.jjenus.qliina_management.common.PageResponse;
import com.jjenus.qliina_management.expense.dto.CreateExpenseRequest;
import com.jjenus.qliina_management.expense.dto.ExpenseDTO;
import com.jjenus.qliina_management.expense.dto.UpdateExpenseRequest;
import com.jjenus.qliina_management.expense.model.ExpenseCategory;
import com.jjenus.qliina_management.expense.service.ExpenseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@Tag(name = "Expenses", description = "Business expense tracking")
@RestController
@RequestMapping("/api/v1/{businessId}/expenses")
@RequiredArgsConstructor
public class ExpenseController {

    private final ExpenseService expenseService;

    @Operation(summary = "List expenses with optional filters")
    @GetMapping
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'report.view.financial')")
    public ResponseEntity<PageResponse<ExpenseDTO>> listExpenses(
            @PathVariable UUID businessId,
            @RequestParam(required = false) UUID shopId,
            @RequestParam(required = false) ExpenseCategory category,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        return ResponseEntity.ok(expenseService.listExpenses(businessId, shopId, category, from, to, page, size));
    }

    @Operation(summary = "Get a single expense")
    @GetMapping("/{id}")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'report.view.financial')")
    public ResponseEntity<ExpenseDTO> getExpense(
            @PathVariable UUID businessId,
            @PathVariable UUID id) {
        return ResponseEntity.ok(expenseService.getExpense(businessId, id));
    }

    @Operation(summary = "Record a new expense")
    @PostMapping
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'expenses.manage')")
    public ResponseEntity<ExpenseDTO> createExpense(
            @PathVariable UUID businessId,
            @Valid @RequestBody CreateExpenseRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(expenseService.createExpense(businessId, req));
    }

    @Operation(summary = "Update an expense")
    @PutMapping("/{id}")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'expenses.manage')")
    public ResponseEntity<ExpenseDTO> updateExpense(
            @PathVariable UUID businessId,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateExpenseRequest req) {
        return ResponseEntity.ok(expenseService.updateExpense(businessId, id, req));
    }

    @Operation(summary = "Delete an expense")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'expenses.manage')")
    public ResponseEntity<Void> deleteExpense(
            @PathVariable UUID businessId,
            @PathVariable UUID id) {
        expenseService.deleteExpense(businessId, id);
        return ResponseEntity.noContent().build();
    }
}
