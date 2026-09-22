package com.jjenus.qliina_management.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tenant complaint / support-ticket domain + platform inbox:
 * <ul>
 *   <li>create / list / get / resolve with business-scope permissions</li>
 *   <li>SLA computed from severity (URGENT 8h, LOW 72h)</li>
 *   <li>cross-tenant isolation (404, no existence leak)</li>
 *   <li>terminal states cannot be reopened; closing requires a note</li>
 *   <li>platform inbox with SUPPORT_AGENT read-only contract</li>
 * </ul>
 */
class ComplaintIntegrationTest extends BaseIntegrationTest {

    private String complaintBase(UUID businessId) {
        return "/api/v1/" + businessId + "/complaints";
    }

    private Map<String, Object> complaintBody(String customer, String category,
                                              String severity, String description) {
        Map<String, Object> body = new HashMap<>();
        body.put("customerName", customer);
        body.put("category", category);
        body.put("severity", severity);
        body.put("description", description);
        return body;
    }

    private UUID createComplaint(AuthContext ctx, String customer, String severity) throws Exception {
        MvcResult res = post(complaintBase(ctx.businessId()), ctx.accessToken(),
                        complaintBody(customer, "SERVICE", severity, "Complaint about " + customer))
                .andExpect(status().isCreated())
                .andReturn();
        return extractUuid(res, "$.id");
    }

    private String washerToken(AuthContext ctx) throws Exception {
        MvcResult rolesRes = get("/api/v1/" + ctx.businessId() + "/users/available-roles", ctx.accessToken())
                .andExpect(status().isOk())
                .andReturn();
        String rolesJson = rolesRes.getResponse().getContentAsString(StandardCharsets.UTF_8);
        List<String> names = JsonPath.read(rolesJson, "$[*].name");
        List<String> ids = JsonPath.read(rolesJson, "$[*].id");
        int idx = names.indexOf("WASHER");
        if (idx < 0) throw new IllegalStateException("WASHER role not found");

        String unique = random();
        Map<String, Object> body = new HashMap<>();
        body.put("username", "washer_" + unique);
        body.put("email", "washer_" + unique + "@test.com");
        body.put("phone", "+1" + (555_300_0000L + counter.incrementAndGet()));
        body.put("firstName", "Wash");
        body.put("lastName", "Er");
        body.put("password", DEFAULT_PASSWORD);
        body.put("confirmPassword", DEFAULT_PASSWORD);
        body.put("roles", List.of(Map.of("roleId", ids.get(idx), "shopId", ctx.shopId().toString())));

        post("/api/v1/" + ctx.businessId() + "/users", ctx.accessToken(), body)
                .andExpect(status().isOk());
        // Clock in so the @RequireClockIn gate is not the reason for any denial.
        post("/api/v1/" + ctx.businessId() + "/employees/clock-in",
                loginToken("washer_" + unique, DEFAULT_PASSWORD),
                Map.of("shopId", ctx.shopId().toString()))
                .andExpect(status().isOk());
        return loginToken("washer_" + unique, DEFAULT_PASSWORD);
    }

    private String supportAgentToken() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("username", "support_" + random());
        body.put("email", "support_" + random() + "@test.com");
        body.put("firstName", "Platform");
        body.put("lastName", "Staff");
        body.put("role", "SUPPORT_AGENT");
        body.put("temporaryPassword", "Temp@12345");
        MvcResult res = post("/api/v1/admin/platform-users", adminToken(), body)
                .andExpect(status().isOk())
                .andReturn();
        String uname = readString(res.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.username");
        return loginToken(uname, "Temp@12345");
    }

    // ---------------------------------------------------------------------
    // Tenant workflow
    // ---------------------------------------------------------------------

    @Test
    void businessAdmin_createListAndGetComplaints() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();

        MvcResult created = post(complaintBase(ctx.businessId()), ctx.accessToken(),
                        complaintBody("Jane Doe", "QUALITY", "MEDIUM", "Shirt came back stained"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.complaintNumber").value("CMP-00001"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.category").value("QUALITY"))
                .andExpect(jsonPath("$.businessId").value(ctx.businessId().toString()))
                .andExpect(jsonPath("$.overdue").value(false))
                .andReturn();
        UUID firstId = extractUuid(created, "$.id");

        // Sequential numbering per business.
        post(complaintBase(ctx.businessId()), ctx.accessToken(),
                complaintBody("Bob Smith", "DELIVERY", "URGENT", "Package handed to wrong neighbor"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.complaintNumber").value("CMP-00002"));

        get(complaintBase(ctx.businessId()), ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].complaintNumber").value("CMP-00002"));
        get(complaintBase(ctx.businessId()) + "/" + firstId, ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerName").value("Jane Doe"))
                .andExpect(jsonPath("$.severity").value("MEDIUM"));
    }

    @Test
    void createComplaint_slaComputedFromSeverity() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        LocalDateTime before = LocalDateTime.now().plusHours(7);

        MvcResult urgent = post(complaintBase(ctx.businessId()), ctx.accessToken(),
                        complaintBody("Urgent Case", "BILLING", "URGENT", "Double charged"))
                .andExpect(status().isCreated())
                .andReturn();
        MvcResult low = post(complaintBase(ctx.businessId()), ctx.accessToken(),
                        complaintBody("Slow Case", "OTHER", "LOW", "Minor question"))
                .andExpect(status().isCreated())
                .andReturn();

        // Business-scoped responses serialize LocalDateTime with the business zone
        // offset (Zone-aware Jackson serializer) — parse via OffsetDateTime.
        LocalDateTime urgentSla = OffsetDateTime.parse(readString(
                urgent.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.slaDueAt")).toLocalDateTime();
        LocalDateTime lowSla = OffsetDateTime.parse(readString(
                low.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.slaDueAt")).toLocalDateTime();

        // URGENT ≈ +8h, LOW ≈ +72h.
        assertThat(urgentSla.isAfter(before)).isTrue();
        assertThat(urgentSla.isBefore(LocalDateTime.now().plusHours(9))).isTrue();
        assertThat(lowSla.isAfter(LocalDateTime.now().plusHours(70))).isTrue();
        assertThat(lowSla.isBefore(LocalDateTime.now().plusHours(74))).isTrue();
    }

    @Test
    void tenant_canFilterByStatusAndSeverity() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        createComplaint(ctx, "A", "URGENT");
        createComplaint(ctx, "B", "LOW");

        get(complaintBase(ctx.businessId()) + "?severity=URGENT", ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].customerName").value("A"));
        get(complaintBase(ctx.businessId()) + "?status=RESOLVED", ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void resolveComplaint_requiresResolutionNote() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        UUID id = createComplaint(ctx, "Resolver", "HIGH");

        // Closing without a note is rejected (Business Rule Violation on resolutionNote).
        assertProblemDetail(patch(complaintBase(ctx.businessId()) + "/" + id + "/status",
                ctx.accessToken(), Map.of("status", "RESOLVED")), 400, "VALIDATION_ERROR");
        assertProblemDetail(patch(complaintBase(ctx.businessId()) + "/" + id + "/status",
                ctx.accessToken(), Map.of("status", "REJECTED")), 400, "VALIDATION_ERROR");

        // Triage to IN_REVIEW needs no note.
        patch(complaintBase(ctx.businessId()) + "/" + id + "/status",
                ctx.accessToken(), Map.of("status", "IN_REVIEW"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_REVIEW"));

        // Resolution closes the ticket and stamps who/when.
        patch(complaintBase(ctx.businessId()) + "/" + id + "/status",
                ctx.accessToken(), Map.of("status", "RESOLVED", "resolutionNote", "Re-washed and returned"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.resolutionNote").value("Re-washed and returned"))
                .andExpect(jsonPath("$.resolvedAt").exists())
                .andExpect(jsonPath("$.resolvedBy").isNotEmpty());
    }

    @Test
    void resolvedComplaint_cannotBeReopened() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        UUID id = createComplaint(ctx, "Maybe", "MEDIUM");
        patch(complaintBase(ctx.businessId()) + "/" + id + "/status",
                ctx.accessToken(), Map.of("status", "RESOLVED", "resolutionNote", "Done"))
                .andExpect(status().isOk());

        assertProblemDetail(patch(complaintBase(ctx.businessId()) + "/" + id + "/status",
                ctx.accessToken(), Map.of("status", "OPEN")), 400, "INVALID_STATUS_TRANSITION");
        assertProblemDetail(patch(complaintBase(ctx.businessId()) + "/" + id + "/status",
                ctx.accessToken(), Map.of("status", "REJECTED", "resolutionNote", "Attempted")),
                400, "INVALID_STATUS_TRANSITION");
    }

    @Test
    void washerWithoutPermission_cannotCreateComplaint() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        String washer = washerToken(ctx);
        // WASHER lacks complaint.create (and the role matrix says so) → 403.
        assertProblemDetail(post(complaintBase(ctx.businessId()), washer,
                complaintBody("No", "OTHER", "LOW", "nope")), 403, "ACCESS_DENIED");
    }

    // ---------------------------------------------------------------------
    // Cross-tenant isolation
    // ---------------------------------------------------------------------

    @Test
    void tenantA_cannotReadOrUpdateTenantB_Complaint() throws Exception {
        AuthContext a = registerBusinessAndOwner();
        AuthContext b = registerBusinessAndOwner();
        UUID complaintB = createComplaint(b, "Tenant B Customer", "HIGH");

        // Error-code convention in this codebase: missing entities surface as
        // 400 + *_NOT_FOUND (same as a genuinely-missing complaint → no leak).
        assertProblemDetail(get(complaintBase(a.businessId()) + "/" + complaintB, a.accessToken()),
                400, "COMPLAINT_NOT_FOUND");
        assertProblemDetail(patch(complaintBase(a.businessId()) + "/" + complaintB + "/status",
                a.accessToken(), Map.of("status", "RESOLVED", "resolutionNote", "sneaky")),
                400, "COMPLAINT_NOT_FOUND");
    }

    // ---------------------------------------------------------------------
    // Platform inbox
    // ---------------------------------------------------------------------

    @Test
    void superAdmin_canListFilterAndTriageAcrossTenants() throws Exception {
        AuthContext a = registerBusinessAndOwner();
        AuthContext b = registerBusinessAndOwner();
        UUID urgent = createComplaint(a, "Urgent One", "URGENT");
        createComplaint(a, "Urgent Two", "URGENT");
        createComplaint(b, "Other Tenant", "LOW");

        String admin = adminToken();

        // Counts are scoped per business (fresh per test) to stay deterministic;
        // the global unfiltered inbox also contains complaints from other tests.
        get("/api/v1/admin/complaints", admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(org.hamcrest.Matchers.greaterThanOrEqualTo(3)));
        get("/api/v1/admin/complaints?businessId=" + a.businessId() + "&severity=URGENT", admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
        get("/api/v1/admin/complaints?businessId=" + b.businessId(), admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].customerName").value("Other Tenant"));

        // Platform triage resolves any tenant's ticket.
        patch("/api/v1/admin/complaints/" + urgent + "/status", admin,
                Map.of("status", "RESOLVED", "resolutionNote", "Escalated to refund"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.resolvedAt").exists());
    }

    @Test
    void supportAgent_isReadOnlyOnComplaintInbox() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        UUID id = createComplaint(ctx, "Viewer", "LOW");
        String agent = supportAgentToken();

        // View allowed... (scoped to this test's business for deterministic totals)
        get("/api/v1/admin/complaints?businessId=" + ctx.businessId(), agent)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        get("/api/v1/admin/complaints/" + id, agent)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.complaintNumber").value("CMP-00001"));
        // ...but triage / resolve is platform.complaints.manage → 403.
        assertProblemDetail(patch("/api/v1/admin/complaints/" + id + "/status", agent,
                Map.of("status", "IN_REVIEW")), 403, "ACCESS_DENIED");
    }
}