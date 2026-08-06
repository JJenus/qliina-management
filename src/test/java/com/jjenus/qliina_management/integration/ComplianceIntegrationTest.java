package com.jjenus.qliina_management.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Compliance suite: ComplianceController — /api/v1/{businessId}/compliance
 * (retention policies, consent management, data subject requests, security events,
 * GDPR reports).
 *
 * Retention/security/DSR/report endpoints require 'admin.settings' or 'admin.audit'
 * (owner-only); consent endpoints use customer.* permissions.
 */
class ComplianceIntegrationTest extends BaseIntegrationTest {

    private String base(UUID businessId) {
        return "/api/v1/" + businessId + "/compliance";
    }

    private UUID createCustomer(AuthContext ctx) throws Exception {
        String json = post("/api/v1/" + ctx.businessId() + "/customers", ctx.accessToken(), Map.of(
                "firstName", "Compliance",
                "lastName", "Customer",
                "phone", "+1" + (555_700_0000L + counter.incrementAndGet())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return readUuid(json, "$.id");
    }

    private Map<String, Object> retentionBody() {
        Map<String, Object> body = new HashMap<>();
        body.put("entityType", "ORDER");
        body.put("retentionDays", 365);
        return body;
    }

    // ---------------------------------------------------------------------
    // Retention policies
    // ---------------------------------------------------------------------

    @Test
    void retention_list_empty() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        get(base(ctx.businessId()) + "/retention/policies", ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void retention_create() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        post(base(ctx.businessId()) + "/retention/policies", ctx.accessToken(), retentionBody())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entityType").value("ORDER"))
                .andExpect(jsonPath("$.retentionDays").value(365))
                .andExpect(jsonPath("$.id").exists());
    }

    @Test
    void retention_createValidation() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertValidation(post(base(ctx.businessId()) + "/retention/policies", ctx.accessToken(), Map.of()),
                Map.of("entityType", "Entity type is required", "retentionDays", "Retention days is required"));
    }

    @Test
    void retention_duplicate_badRequest() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        post(base(ctx.businessId()) + "/retention/policies", ctx.accessToken(), retentionBody())
                .andExpect(status().isOk());
        assertProblemDetail(post(base(ctx.businessId()) + "/retention/policies", ctx.accessToken(), retentionBody()),
                400, "POLICY_EXISTS");
    }

    @Test
    void retention_update() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        MvcResult createRes = post(base(ctx.businessId()) + "/retention/policies", ctx.accessToken(), retentionBody())
                .andExpect(status().isOk()).andReturn();
        UUID policyId = extractUuid(createRes, "$.id");

        Map<String, Object> update = retentionBody();
        update.put("retentionDays", 180);
        put(base(ctx.businessId()) + "/retention/policies/" + policyId, ctx.accessToken(), update)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.retentionDays").value(180));
    }

    @Test
    void retention_update_notFound() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertProblemDetail(put(base(ctx.businessId()) + "/retention/policies/" + UUID.randomUUID(),
                ctx.accessToken(), retentionBody()), 400, "POLICY_NOT_FOUND");
    }

    @Test
    void retention_delete() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        MvcResult createRes = post(base(ctx.businessId()) + "/retention/policies", ctx.accessToken(), retentionBody())
                .andExpect(status().isOk()).andReturn();
        UUID policyId = extractUuid(createRes, "$.id");

        assertSuccess(delete(base(ctx.businessId()) + "/retention/policies/" + policyId, ctx.accessToken()),
                "Policy deleted successfully");

        get(base(ctx.businessId()) + "/retention/policies", ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void retention_delete_notFound() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertProblemDetail(delete(base(ctx.businessId()) + "/retention/policies/" + UUID.randomUUID(),
                ctx.accessToken()), 400, "POLICY_NOT_FOUND");
    }

    @Test
    void retention_cleanup() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        post(base(ctx.businessId()) + "/retention/policies", ctx.accessToken(), retentionBody())
                .andExpect(status().isOk());
        post(base(ctx.businessId()) + "/retention/cleanup", ctx.accessToken(), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").exists())
                .andExpect(jsonPath("$.totalRecordsProcessed").isNumber())
                .andExpect(jsonPath("$.executionTimeMs").isNumber());
    }

    // ---------------------------------------------------------------------
    // Consent management
    // ---------------------------------------------------------------------

    @Test
    void consent_list_noConsents() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        UUID customerId = createCustomer(ctx);
        get(base(ctx.businessId()) + "/consent/" + customerId, ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void consent_record() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        UUID customerId = createCustomer(ctx);
        post(base(ctx.businessId()) + "/consent/" + customerId, ctx.accessToken(), Map.of(
                "consentType", "MARKETING",
                "granted", true,
                "consentVersion", "v1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consentType").value("MARKETING"))
                .andExpect(jsonPath("$.granted").value(true))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.customerId").value(customerId.toString()));
    }

    @Test
    void consent_recordValidation() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        UUID customerId = createCustomer(ctx);
        assertValidation(post(base(ctx.businessId()) + "/consent/" + customerId, ctx.accessToken(), Map.of()),
                Map.of("consentType", "Consent type is required", "granted", "Granted flag is required"));
    }

    @Test
    void consent_record_invalidType() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        UUID customerId = createCustomer(ctx);
        assertProblemDetail(post(base(ctx.businessId()) + "/consent/" + customerId, ctx.accessToken(), Map.of(
                "consentType", "NOT_A_TYPE", "granted", true)), 400, "INVALID_REQUEST");
    }

    @Test
    void consent_record_unknownCustomer() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertProblemDetail(post(base(ctx.businessId()) + "/consent/" + UUID.randomUUID(), ctx.accessToken(), Map.of(
                "consentType", "MARKETING", "granted", true)), 400, "CUSTOMER_NOT_FOUND");
    }

    @Test
    void consent_list_unknownCustomer() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertProblemDetail(get(base(ctx.businessId()) + "/consent/" + UUID.randomUUID(), ctx.accessToken()),
                400, "CUSTOMER_NOT_FOUND");
    }

    @Test
    void consent_crossTenant_customer() throws Exception {
        AuthContext ctxA = registerBusinessAndOwner();
        AuthContext ctxB = registerBusinessAndOwner();
        UUID customerA = createCustomer(ctxA);
        assertProblemDetail(post(base(ctxB.businessId()) + "/consent/" + customerA, ctxB.accessToken(), Map.of(
                "consentType", "MARKETING", "granted", true)), 400, "CUSTOMER_NOT_FOUND");
    }

    @Test
    void consent_revoke() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        UUID customerId = createCustomer(ctx);
        post(base(ctx.businessId()) + "/consent/" + customerId, ctx.accessToken(), Map.of(
                "consentType", "EMAIL", "granted", true))
                .andExpect(status().isOk());

        assertSuccess(delete(base(ctx.businessId()) + "/consent/" + customerId + "/EMAIL", ctx.accessToken()),
                "Consent revoked successfully");

        get(base(ctx.businessId()) + "/consent/" + customerId, ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("REVOKED"));
    }

    // ---------------------------------------------------------------------
    // Data subject requests
    // ---------------------------------------------------------------------

    @Test
    void dsr_list_empty() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertPage(get(base(ctx.businessId()) + "/dsr", ctx.accessToken()));
    }

    @Test
    void dsr_create() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        post(base(ctx.businessId()) + "/dsr", ctx.accessToken(), Map.of(
                "customerName", "Jane Doe",
                "customerEmail", "jane@test.com",
                "requestType", "ACCESS",
                "requestDetails", "Export my data"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestNumber").exists())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.requestType").value("ACCESS"));
    }

    @Test
    void dsr_createValidation() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertValidation(post(base(ctx.businessId()) + "/dsr", ctx.accessToken(), Map.of()),
                Map.of("customerName", "Customer name is required",
                        "customerEmail", "Customer email is required",
                        "requestType", "Request type is required"));
    }

    @Test
    void dsr_create_badEmail() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertValidation(post(base(ctx.businessId()) + "/dsr", ctx.accessToken(), Map.of(
                "customerName", "Jane",
                "customerEmail", "not-an-email",
                "requestType", "ACCESS")),
                Map.of("customerEmail", "Invalid email format"));
    }

    @Test
    void dsr_create_invalidType() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertProblemDetail(post(base(ctx.businessId()) + "/dsr", ctx.accessToken(), Map.of(
                "customerName", "Jane",
                "customerEmail", "jane@test.com",
                "requestType", "NOT_A_TYPE")), 400, "INVALID_REQUEST");
    }

    @Test
    void dsr_get_and_update_and_process() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        MvcResult createRes = post(base(ctx.businessId()) + "/dsr", ctx.accessToken(), Map.of(
                "customerName", "Jane Doe",
                "customerEmail", "jane@test.com",
                "requestType", "PORTABILITY"))
                .andExpect(status().isOk()).andReturn();
        UUID requestId = extractUuid(createRes, "$.id");

        get(base(ctx.businessId()) + "/dsr/" + requestId, ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(requestId.toString()));

        put(base(ctx.businessId()) + "/dsr/" + requestId, ctx.accessToken(), Map.of(
                "status", "VERIFYING",
                "notes", "Awaiting verification"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VERIFYING"));

        post(base(ctx.businessId()) + "/dsr/" + requestId + "/process", ctx.accessToken(), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    @Test
    void dsr_get_notFound() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertProblemDetail(get(base(ctx.businessId()) + "/dsr/" + UUID.randomUUID(), ctx.accessToken()),
                400, "REQUEST_NOT_FOUND");
    }

    @Test
    void dsr_update_invalidStatus() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        MvcResult createRes = post(base(ctx.businessId()) + "/dsr", ctx.accessToken(), Map.of(
                "customerName", "Jane Doe",
                "customerEmail", "jane@test.com",
                "requestType", "ACCESS"))
                .andExpect(status().isOk()).andReturn();
        UUID requestId = extractUuid(createRes, "$.id");
        assertProblemDetail(put(base(ctx.businessId()) + "/dsr/" + requestId, ctx.accessToken(), Map.of(
                "status", "NOPE")), 400, "INVALID_REQUEST");
    }

    // ---------------------------------------------------------------------
    // Security events
    // ---------------------------------------------------------------------

    @Test
    void securityEvents_list_empty() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertPage(get(base(ctx.businessId()) + "/security/events", ctx.accessToken()));
    }

    @Test
    void blockIp() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertSuccess(post(base(ctx.businessId()) + "/security/block/192.168.0.1?reason=test", ctx.accessToken(), null),
                "IP blocked successfully");
    }

    @Test
    void blockIp_missingReason() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertProblemDetail(post(base(ctx.businessId()) + "/security/block/192.168.0.1", ctx.accessToken(), null),
                400, "MISSING_PARAMETER");
    }

    @Test
    void resolveBlock_notFound() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertProblemDetail(post(base(ctx.businessId()) + "/security/resolve/" + UUID.randomUUID(),
                ctx.accessToken(), null), 400, "EVENT_NOT_FOUND");
    }

    // ---------------------------------------------------------------------
    // Compliance reports
    // ---------------------------------------------------------------------

    @Test
    void reports_generateGdpr() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        post(base(ctx.businessId()) + "/reports/gdpr", ctx.accessToken(), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reportNumber").exists())
                .andExpect(jsonPath("$.reportType").value("GDPR_COMPLIANCE"))
                .andExpect(jsonPath("$.status").value("GENERATED"));
    }

    @Test
    void reports_list_and_get() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        MvcResult gdprRes = post(base(ctx.businessId()) + "/reports/gdpr", ctx.accessToken(), null)
                .andExpect(status().isOk()).andReturn();
        UUID reportId = extractUuid(gdprRes, "$.id");

        assertPage(get(base(ctx.businessId()) + "/reports", ctx.accessToken()));

        get(base(ctx.businessId()) + "/reports?reportType=GDPR", ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());

        get(base(ctx.businessId()) + "/reports/" + reportId, ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(reportId.toString()));
    }

    @Test
    void reports_get_notFound() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertProblemDetail(get(base(ctx.businessId()) + "/reports/" + UUID.randomUUID(), ctx.accessToken()),
                400, "REPORT_NOT_FOUND");
    }

    @Test
    void reports_download() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        MvcResult gdprRes = post(base(ctx.businessId()) + "/reports/gdpr", ctx.accessToken(), null)
                .andExpect(status().isOk()).andReturn();
        UUID reportId = extractUuid(gdprRes, "$.id");
        get(base(ctx.businessId()) + "/reports/" + reportId + "/download", ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"));
    }

    // ---------------------------------------------------------------------
    // Permissions
    // ---------------------------------------------------------------------

    @Test
    void retention_asManager_forbidden() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        String manager = createManager(ctx);
        assertProblemDetail(get(base(ctx.businessId()) + "/retention/policies", manager), 403, "ACCESS_DENIED");
        assertProblemDetail(post(base(ctx.businessId()) + "/retention/policies", manager, retentionBody()),
                403, "ACCESS_DENIED");
    }

    @Test
    void dsr_asManager_forbidden() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        String manager = createManager(ctx);
        assertProblemDetail(get(base(ctx.businessId()) + "/dsr", manager), 403, "ACCESS_DENIED");
    }

    @Test
    void reports_crossTenant_forbidden() throws Exception {
        AuthContext ctxA = registerBusinessAndOwner();
        AuthContext ctxB = registerBusinessAndOwner();
        assertProblemDetail(post(base(ctxA.businessId()) + "/reports/gdpr", ctxB.accessToken(), null),
                403, "ACCESS_DENIED");
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
                "phone", "+1" + (555_750_0000L + counter.incrementAndGet()),
                "firstName", "Shop",
                "lastName", "Manager",
                "password", DEFAULT_PASSWORD,
                "confirmPassword", DEFAULT_PASSWORD,
                "roles", List.of(Map.of("roleId", ids.get(idx), "shopId", ctx.shopId().toString()))))
                .andExpect(status().isOk());

        return loginToken(username, DEFAULT_PASSWORD);
    }
}
