package com.jjenus.qliina_management.integration;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Expense suite: ExpenseController — /api/v1/{businessId}/expenses.
 */
class ExpenseIntegrationTest extends BaseIntegrationTest {

    private String base(UUID businessId) {
        return "/api/v1/" + businessId + "/expenses";
    }

    private Map<String, Object> expenseBody() {
        return Map.of(
                "category", "SUPPLIES",
                "description", "Detergent refill",
                "amount", 45.75,
                "expenseDate", LocalDate.now().toString());
    }

    private UUID createExpense(AuthContext ctx) throws Exception {
        String json = post(base(ctx.businessId()), ctx.accessToken(), expenseBody())
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return readUuid(json, "$.id");
    }

    @Test
    void listExpenses_emptyInitially() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        get(base(ctx.businessId()), ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void createExpense_returnsCreated() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        post(base(ctx.businessId()), ctx.accessToken(), expenseBody())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.category").value("SUPPLIES"))
                .andExpect(jsonPath("$.description").value("Detergent refill"))
                .andExpect(jsonPath("$.amount").value(45.75))
                .andExpect(jsonPath("$.createdBy").exists());
    }

    @Test
    void createExpense_validation() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertValidation(post(base(ctx.businessId()), ctx.accessToken(), Map.of()),
                Map.of(
                        "category", "Category is required",
                        "description", "Description is required",
                        "amount", "Amount is required",
                        "expenseDate", "Expense date is required"));
    }

    @Test
    void getExpense_success() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        UUID expenseId = createExpense(ctx);
        get(base(ctx.businessId()) + "/" + expenseId, ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(expenseId.toString()))
                .andExpect(jsonPath("$.businessId").value(ctx.businessId().toString()));
    }

    @Test
    void getExpense_notFound() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertProblemDetail(get(base(ctx.businessId()) + "/" + UUID.randomUUID(), ctx.accessToken()),
                400, "EXPENSE_NOT_FOUND");
    }

    @Test
    void updateExpense_success() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        UUID expenseId = createExpense(ctx);
        put(base(ctx.businessId()) + "/" + expenseId, ctx.accessToken(),
                Map.of("description", "Updated refill", "amount", 50.0))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(expenseId.toString()))
                .andExpect(jsonPath("$.description").value("Updated refill"))
                .andExpect(jsonPath("$.amount").value(50.0));
    }

    @Test
    void updateExpense_notFound() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertProblemDetail(put(base(ctx.businessId()) + "/" + UUID.randomUUID(),
                ctx.accessToken(), Map.of("amount", 1.0)), 400, "EXPENSE_NOT_FOUND");
    }

    @Test
    void deleteExpense_removesRecord() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        UUID expenseId = createExpense(ctx);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete(base(ctx.businessId()) + "/" + expenseId)
                        .header("Authorization", "Bearer " + ctx.accessToken()))
                .andExpect(status().isNoContent());

        assertProblemDetail(get(base(ctx.businessId()) + "/" + expenseId, ctx.accessToken()),
                400, "EXPENSE_NOT_FOUND");
    }

    @Test
    void listExpenses_filterByCategory() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        createExpense(ctx);
        get(base(ctx.businessId()) + "?category=SUPPLIES", ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        get(base(ctx.businessId()) + "?category=UTILITIES", ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void listExpenses_requiresAuth() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        get(base(ctx.businessId()), null).andExpect(status().isUnauthorized());
    }

    @Test
    void listExpenses_crossTenantDenied() throws Exception {
        AuthContext a = registerBusinessAndOwner();
        AuthContext b = registerBusinessAndOwner();
        get(base(b.businessId()), a.accessToken())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }
}
