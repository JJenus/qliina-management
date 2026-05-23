package com.jjenus.qliina_management.expense.service;

import com.jjenus.qliina_management.common.BusinessException;
import com.jjenus.qliina_management.common.PageResponse;
import com.jjenus.qliina_management.common.util.SecurityContextUtil;
import com.jjenus.qliina_management.expense.dto.CreateExpenseRequest;
import com.jjenus.qliina_management.expense.dto.ExpenseDTO;
import com.jjenus.qliina_management.expense.dto.UpdateExpenseRequest;
import com.jjenus.qliina_management.expense.model.Expense;
import com.jjenus.qliina_management.expense.model.ExpenseCategory;
import com.jjenus.qliina_management.expense.repository.ExpenseRepository;
import com.jjenus.qliina_management.expense.repository.ExpenseSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExpenseService {

    private final ExpenseRepository expenseRepository;

    @Transactional(readOnly = true)
    public PageResponse<ExpenseDTO> listExpenses(
            UUID businessId, UUID shopId, ExpenseCategory category,
            LocalDate from, LocalDate to, int page, int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("expenseDate").descending().and(Sort.by("createdAt").descending()));
        Specification<Expense> spec = ExpenseSpecifications.withFilter(businessId, shopId, category, from, to);
        Page<Expense> result = expenseRepository.findAll(spec, pageable);
        return PageResponse.from(result.map(this::toDTO));
    }

    @Transactional(readOnly = true)
    public ExpenseDTO getExpense(UUID businessId, UUID id) {
        Expense expense = findOwned(businessId, id);
        return toDTO(expense);
    }

    @Transactional
    public ExpenseDTO createExpense(UUID businessId, CreateExpenseRequest req) {
        Expense expense = new Expense();
        expense.setBusinessId(businessId);
        expense.setShopId(req.getShopId());
        expense.setCategory(req.getCategory());
        expense.setDescription(req.getDescription());
        expense.setAmount(req.getAmount());
        expense.setExpenseDate(req.getExpenseDate());
        expense.setPaidTo(req.getPaidTo());
        expense.setReference(req.getReference());
        expense.setNotes(req.getNotes());
        expense.setCreatedBy(SecurityContextUtil.requireUserId());
        return toDTO(expenseRepository.save(expense));
    }

    @Transactional
    public ExpenseDTO updateExpense(UUID businessId, UUID id, UpdateExpenseRequest req) {
        Expense expense = findOwned(businessId, id);
        if (req.getCategory()    != null) expense.setCategory(req.getCategory());
        if (req.getDescription() != null) expense.setDescription(req.getDescription());
        if (req.getAmount()      != null) expense.setAmount(req.getAmount());
        if (req.getExpenseDate() != null) expense.setExpenseDate(req.getExpenseDate());
        if (req.getPaidTo()      != null) expense.setPaidTo(req.getPaidTo());
        if (req.getReference()   != null) expense.setReference(req.getReference());
        if (req.getNotes()       != null) expense.setNotes(req.getNotes());
        if (req.getShopId()      != null) expense.setShopId(req.getShopId());
        return toDTO(expenseRepository.save(expense));
    }

    @Transactional
    public void deleteExpense(UUID businessId, UUID id) {
        Expense expense = findOwned(businessId, id);
        expenseRepository.delete(expense);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Expense findOwned(UUID businessId, UUID id) {
        Expense e = expenseRepository.findById(id)
            .orElseThrow(() -> new BusinessException("Expense not found", "EXPENSE_NOT_FOUND"));
        if (!e.getBusinessId().equals(businessId))
            throw new BusinessException("Expense not found", "EXPENSE_NOT_FOUND");
        return e;
    }

    public ExpenseDTO toDTO(Expense e) {
        return ExpenseDTO.builder()
            .id(e.getId())
            .businessId(e.getBusinessId())
            .shopId(e.getShopId())
            .category(e.getCategory())
            .description(e.getDescription())
            .amount(e.getAmount())
            .expenseDate(e.getExpenseDate())
            .paidTo(e.getPaidTo())
            .reference(e.getReference())
            .notes(e.getNotes())
            .createdBy(e.getCreatedBy())
            .createdAt(e.getCreatedAt())
            .updatedAt(e.getUpdatedAt())
            .build();
    }
}
