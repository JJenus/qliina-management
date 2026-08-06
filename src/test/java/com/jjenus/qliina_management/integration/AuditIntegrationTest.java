package com.jjenus.qliina_management.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Audit suite: AuditController — /api/v1/{businessId}/audit
 * (logs, export, entity history, user activity, summary).
 *
 * All endpoints require the seeded 'admin.audit' permission which only the
 * BUSINESS_ADMIN role carries; other roles and cross-tenant access get 403.
 */
class AuditIntegrationTest extends BaseIntegrationTest {

    private String base(UUID businessId) {
        return "/api/v1/" + businessId + "/audit";
    }

    private String createManager(AuthContext ctx) throws Exception {
        MvcResult rolesRes = get("/api/v1/" + ctx.businessId() + "/users/available-roles", ctx.accessToken())
                .andExpect(status().isOk()).andReturn();
        String rolesJson = rolesRes.getResponse().getContentAsString(StandardCharsets.UTF_8);
        List<String> names = JsonPath.read(rolesJson, "$[*].name");
        List<String> ids = JsonPath.read(rolesJson, "$[*].id");
        int idx = names.indexOf("SHOP_MANAGER");
        if (idx < 0) throw new IllegalStateException("SHOP_MANAGER role not found");

        String unique = random();
        String username = "manager_" + unique;
        String email = username + "@test.com";
        post("/api/v1/" + ctx.businessId() + "/users", ctx.accessToken(), Map.of(
                "username", username,
                "email", email,
                "phone", "+1" + (555_800_0000L + counter.incrementAndGet()),
                "firstName", "Shop",
                "lastName", "Manager",
                "password", DEFAULT_PASSWORD,
                "confirmPassword", DEFAULT_PASSWORD,
                "roles", List.of(Map.of("roleId", ids.get(idx), "shopId", ctx.shopId().toString()))))
                .andExpect(status().isOk());

        return loginToken(username, DEFAULT_PASSWORD);
    }

    // ---------------------------------------------------------------------
    // Logs
    // ---------------------------------------------------------------------

    @Test
    void logs_returnsPage() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertPage(get(base(ctx.businessId()) + "/logs", ctx.accessToken()));
    }

    @Test
    void logs_withFilters() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        String path = base(ctx.businessId()) + "/logs?userId=" + ctx.userId()
                + "&severity=INFO&entityType=ORDER&fromDate=2026-01-01T00:00:00";
        assertPage(get(path, ctx.accessToken()));
    }

    @Test
    void logs_asManager_forbidden() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        String manager = createManager(ctx);
        assertProblemDetail(get(base(ctx.businessId()) + "/logs", manager), 403, "ACCESS_DENIED");
    }

    @Test
    void logs_crossTenant_forbidden() throws Exception {
        AuthContext ctxA = registerBusinessAndOwner();
        AuthContext ctxB = registerBusinessAndOwner();
        assertProblemDetail(get(base(ctxA.businessId()) + "/logs", ctxB.accessToken()), 403, "ACCESS_DENIED");
    }

    @Test
    void logs_withoutToken_unauthorized() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        get(base(ctx.businessId()) + "/logs", null).andExpect(status().isUnauthorized());
    }

    @Test
    void logsExport_returnsCsv() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        get(base(ctx.businessId()) + "/logs/export", ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("text/csv")));
    }

    @Test
    void logsExport_crossTenant_forbidden() throws Exception {
        AuthContext ctxA = registerBusinessAndOwner();
        AuthContext ctxB = registerBusinessAndOwner();
        assertProblemDetail(get(base(ctxA.businessId()) + "/logs/export", ctxB.accessToken()), 403, "ACCESS_DENIED");
    }

    // ---------------------------------------------------------------------
    // Entity history
    // ---------------------------------------------------------------------

    @Test
    void entityHistory_returnsPage() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        String path = base(ctx.businessId()) + "/entity/ORDER/" + UUID.randomUUID();
        assertPage(get(path, ctx.accessToken()));
    }

    @Test
    void entityHistory_crossTenant_forbidden() throws Exception {
        AuthContext ctxA = registerBusinessAndOwner();
        AuthContext ctxB = registerBusinessAndOwner();
        String path = base(ctxA.businessId()) + "/entity/ORDER/" + UUID.randomUUID();
        assertProblemDetail(get(path, ctxB.accessToken()), 403, "ACCESS_DENIED");
    }

    // ---------------------------------------------------------------------
    // User activity
    // ---------------------------------------------------------------------

    @Test
    void userActivity_returnsPage() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertPage(get(base(ctx.businessId()) + "/user/" + ctx.userId(), ctx.accessToken()));
    }

    @Test
    void userActivity_unknownUser_emptyPage() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        get(base(ctx.businessId()) + "/user/" + UUID.randomUUID(), ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    // ---------------------------------------------------------------------
    // Summary
    // ---------------------------------------------------------------------

    @Test
    void summary_returnsSummary() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        String from = LocalDateTime.now().minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        String to = LocalDateTime.now().plusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        get(base(ctx.businessId()) + "/summary?startDate=" + from + "&endDate=" + to, ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalEvents").isNumber())
                .andExpect(jsonPath("$.criticalEvents").isNumber())
                .andExpect(jsonPath("$.warningEvents").isNumber())
                .andExpect(jsonPath("$.topActions").isArray());
    }

    @Test
    void summary_missingParams_badRequest() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertProblemDetail(get(base(ctx.businessId()) + "/summary", ctx.accessToken()), 400, "MISSING_PARAMETER");
    }

    @Test
    void summary_crossTenant_forbidden() throws Exception {
        AuthContext ctxA = registerBusinessAndOwner();
        AuthContext ctxB = registerBusinessAndOwner();
        String from = LocalDateTime.now().minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        String to = LocalDateTime.now().plusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        assertProblemDetail(get(base(ctxA.businessId()) + "/summary?startDate=" + from + "&endDate=" + to,
                ctxB.accessToken()), 403, "ACCESS_DENIED");
    }
}
