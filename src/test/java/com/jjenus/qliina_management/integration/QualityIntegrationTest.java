package com.jjenus.qliina_management.integration;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Quality suite: QualityController — /api/v1/{businessId}/quality.
 * (Checklists, quality checks, defects, scorecards, analytics.)
 */
class QualityIntegrationTest extends BaseIntegrationTest {

    private String base(UUID businessId) {
        return "/api/v1/" + businessId + "/quality";
    }

    private UUID createChecklist(AuthContext ctx) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("name", "Wash Standard");
        body.put("items", List.of(Map.of("description", "No stains remaining")));
        String json = post(base(ctx.businessId()) + "/checklists", ctx.accessToken(), body)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return readUuid(json, "$.id");
    }

    private UUID reportDefect(AuthContext ctx) throws Exception {
        String json = post(base(ctx.businessId()) + "/orders/" + UUID.randomUUID() + "/items/"
                + UUID.randomUUID() + "/defect", ctx.accessToken(),
                Map.of("type", "STAIN", "severity", "MINOR", "description", "Residual stain"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return readUuid(json, "$.id");
    }

    // ---------------------------------------------------------------------
    // Checklists
    // ---------------------------------------------------------------------

    @Test
    void createAndGetChecklist() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        UUID checklistId = createChecklist(ctx);

        get(base(ctx.businessId()) + "/checklists/" + checklistId, ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(checklistId.toString()))
                .andExpect(jsonPath("$.name").value("Wash Standard"))
                .andExpect(jsonPath("$.items[0].description").value("No stains remaining"));

        get(base(ctx.businessId()) + "/checklists", ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void createChecklist_validation() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertValidation(post(base(ctx.businessId()) + "/checklists", ctx.accessToken(), Map.of()),
                Map.of(
                        "name", "Name is required",
                        "items", "Items are required"));
    }

    @Test
    void getChecklist_notFound() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertProblemDetail(get(base(ctx.businessId()) + "/checklists/" + UUID.randomUUID(), ctx.accessToken()),
                400, "CHECKLIST_NOT_FOUND");
    }

    @Test
    void updateChecklist_success() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        UUID checklistId = createChecklist(ctx);
        put(base(ctx.businessId()) + "/checklists/" + checklistId, ctx.accessToken(),
                Map.of("name", "Premium Wash Standard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(checklistId.toString()))
                .andExpect(jsonPath("$.name").value("Premium Wash Standard"));
    }

    @Test
    void updateChecklist_notFound() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertProblemDetail(put(base(ctx.businessId()) + "/checklists/" + UUID.randomUUID(),
                ctx.accessToken(), Map.of("name", "x")), 400, "CHECKLIST_NOT_FOUND");
    }

    // ---------------------------------------------------------------------
    // Quality checks
    // ---------------------------------------------------------------------

    @Test
    void performQualityCheck_passed() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        UUID checklistId = createChecklist(ctx);
        String checklistJson = get(base(ctx.businessId()) + "/checklists/" + checklistId, ctx.accessToken())
                .andReturn().getResponse().getContentAsString();
        String itemId = readString(checklistJson, "$.items[0].id");

        Map<String, Object> body = new HashMap<>();
        body.put("checklistId", checklistId.toString());
        body.put("results", List.of(Map.of("checklistItemId", itemId, "passed", true, "notes", "ok")));

        post(base(ctx.businessId()) + "/orders/" + UUID.randomUUID() + "/items/" + UUID.randomUUID()
                + "/quality-check", ctx.accessToken(), body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PASSED"))
                .andExpect(jsonPath("$.checkResults[0].passed").value(true))
                .andExpect(jsonPath("$.checkedBy").exists());
    }

    @Test
    void performQualityCheck_failed() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        UUID checklistId = createChecklist(ctx);
        String checklistJson = get(base(ctx.businessId()) + "/checklists/" + checklistId, ctx.accessToken())
                .andReturn().getResponse().getContentAsString();
        String itemId = readString(checklistJson, "$.items[0].id");

        Map<String, Object> body = new HashMap<>();
        body.put("checklistId", checklistId.toString());
        body.put("results", List.of(Map.of("checklistItemId", itemId, "passed", false)));

        post(base(ctx.businessId()) + "/orders/" + UUID.randomUUID() + "/items/" + UUID.randomUUID()
                + "/quality-check", ctx.accessToken(), body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.nextAction").exists());
    }

    @Test
    void performQualityCheck_emptyResults() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        post(base(ctx.businessId()) + "/orders/" + UUID.randomUUID() + "/items/" + UUID.randomUUID()
                + "/quality-check", ctx.accessToken(), Map.of("results", new ArrayList<Object>()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PASSED"))
                .andExpect(jsonPath("$.checkResults").isArray());
    }

    // ---------------------------------------------------------------------
    // Defects
    // ---------------------------------------------------------------------

    @Test
    void reportAndResolveDefect() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        UUID defectId = reportDefect(ctx);

        get(base(ctx.businessId()) + "/defects", ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(defectId.toString()))
                .andExpect(jsonPath("$.content[0].status").value("OPEN"));

        put(base(ctx.businessId()) + "/defects/" + defectId, ctx.accessToken(),
                Map.of("status", "RESOLVED", "resolution", "Rewashed", "compensation", 5.0))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.resolution").value("Rewashed"));
    }

    @Test
    void reportDefect_validation() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertValidation(post(base(ctx.businessId()) + "/orders/" + UUID.randomUUID() + "/items/"
                + UUID.randomUUID() + "/defect", ctx.accessToken(), Map.of()),
                Map.of(
                        "type", "Defect type is required",
                        "severity", "Severity is required",
                        "description", "Description is required"));
    }

    @Test
    void updateDefect_notFound() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertProblemDetail(put(base(ctx.businessId()) + "/defects/" + UUID.randomUUID(),
                ctx.accessToken(), Map.of("status", "RESOLVED")), 400, "DEFECT_NOT_FOUND");
    }

    @Test
    void resolveDefect_success() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        UUID defectId = reportDefect(ctx);

        assertSuccess(post(base(ctx.businessId()) + "/defects/" + defectId + "/resolve?resolution=Replaced",
                ctx.accessToken(), null), "Defect resolved successfully");

        get(base(ctx.businessId()) + "/defects", ctx.accessToken())
                .andExpect(jsonPath("$.content[0].status").value("RESOLVED"));
    }

    @Test
    void resolveDefect_notFound() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertProblemDetail(post(base(ctx.businessId()) + "/defects/" + UUID.randomUUID()
                + "/resolve?resolution=Replaced", ctx.accessToken(), null),
                400, "DEFECT_NOT_FOUND");
    }

    @Test
    void listDefects_requiresAuth() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        get(base(ctx.businessId()) + "/defects", null).andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------------
    // Scorecards & analytics
    // ---------------------------------------------------------------------

    @Test
    void getEmployeeScorecards_empty() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        LocalDate today = LocalDate.now();
        get(base(ctx.businessId()) + "/scorecards/employees?startDate=" + today.minusDays(7)
                + "&endDate=" + today, ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void getEmployeeScorecard_owner() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        LocalDate today = LocalDate.now();
        get(base(ctx.businessId()) + "/scorecards/employees/" + ctx.userId() + "?startDate="
                + today.minusDays(7) + "&endDate=" + today, ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeId").value(ctx.userId().toString()))
                .andExpect(jsonPath("$.itemsProcessed").value(0))
                .andExpect(jsonPath("$.passRate").value(0.0))
                .andExpect(jsonPath("$.trend").isArray());
    }

    @Test
    void getDefectTypeDistribution_empty() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        get(base(ctx.businessId()) + "/analytics/defects/by-type?startDate=2026-01-01T00:00:00"
                + "&endDate=2026-01-31T23:59:59", ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isMap());
    }

    @Test
    void getSeverityDistribution_afterDefect() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        reportDefect(ctx);
        get(base(ctx.businessId()) + "/analytics/defects/by-severity?startDate=2026-01-01T00:00:00"
                + "&endDate=" + java.time.LocalDateTime.now().plusDays(1).toString(), ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.MINOR").value(1));
    }
}
