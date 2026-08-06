package com.jjenus.qliina_management.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Item Audit suite: ItemAuditController — /api/v1/{businessId}/audit/items
 * (trail, my-interactions).
 *
 * Trail requires 'admin.audit' OR 'order.view'; my-interactions requires
 * 'order.view'. Roles carrying order.view (manager, front desk, washer) succeed;
 * the access is additionally scoped to the requesting user for my-interactions.
 */
class ItemAuditIntegrationTest extends BaseIntegrationTest {

    private String base(UUID businessId) {
        return "/api/v1/" + businessId + "/audit/items";
    }

    private String createEmployee(AuthContext ctx, String roleName) throws Exception {
        MvcResult rolesRes = get("/api/v1/" + ctx.businessId() + "/users/available-roles", ctx.accessToken())
                .andExpect(status().isOk()).andReturn();
        String rolesJson = rolesRes.getResponse().getContentAsString(StandardCharsets.UTF_8);
        List<String> names = JsonPath.read(rolesJson, "$[*].name");
        List<String> ids = JsonPath.read(rolesJson, "$[*].id");
        int idx = names.indexOf(roleName);
        if (idx < 0) throw new IllegalStateException("Role not found: " + roleName);

        String unique = random();
        String username = roleName.toLowerCase() + "_" + unique;
        post("/api/v1/" + ctx.businessId() + "/users", ctx.accessToken(), Map.of(
                "username", username,
                "email", username + "@test.com",
                "phone", "+1" + (555_650_0000L + counter.incrementAndGet()),
                "firstName", roleName,
                "lastName", "Staff",
                "password", DEFAULT_PASSWORD,
                "confirmPassword", DEFAULT_PASSWORD,
                "roles", List.of(Map.of("roleId", ids.get(idx), "shopId", ctx.shopId().toString()))))
                .andExpect(status().isOk());

        return loginToken(username, DEFAULT_PASSWORD);
    }

    // ---------------------------------------------------------------------
    // Trail
    // ---------------------------------------------------------------------

    @Test
    void trail_returnsPage() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertPage(get(base(ctx.businessId()) + "/" + UUID.randomUUID() + "/trail", ctx.accessToken()));
    }

    @Test
    void trail_asWasher_allowed() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        String washer = createEmployee(ctx, "WASHER");
        get(base(ctx.businessId()) + "/" + UUID.randomUUID() + "/trail", washer)
                .andExpect(status().isOk());
    }

    @Test
    void trail_asFrontDesk_allowed() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        String frontDesk = createEmployee(ctx, "FRONT_DESK");
        get(base(ctx.businessId()) + "/" + UUID.randomUUID() + "/trail", frontDesk)
                .andExpect(status().isOk());
    }

    @Test
    void trail_crossTenant_forbidden() throws Exception {
        AuthContext ctxA = registerBusinessAndOwner();
        AuthContext ctxB = registerBusinessAndOwner();
        assertProblemDetail(get(base(ctxA.businessId()) + "/" + UUID.randomUUID() + "/trail", ctxB.accessToken()),
                403, "ACCESS_DENIED");
    }

    @Test
    void trail_withoutToken_unauthorized() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        get(base(ctx.businessId()) + "/" + UUID.randomUUID() + "/trail", null)
                .andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------------
    // My interactions
    // ---------------------------------------------------------------------

    @Test
    void myInteractions_returnsPage() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        get(base(ctx.businessId()) + "/" + UUID.randomUUID() + "/my-interactions", ctx.accessToken())
                .andExpect(status().isOk());
    }

    @Test
    void myInteractions_asWasher_allowed() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        String washer = createEmployee(ctx, "WASHER");
        get(base(ctx.businessId()) + "/" + UUID.randomUUID() + "/my-interactions", washer)
                .andExpect(status().isOk());
    }

    @Test
    void myInteractions_crossTenant_forbidden() throws Exception {
        AuthContext ctxA = registerBusinessAndOwner();
        AuthContext ctxB = registerBusinessAndOwner();
        assertProblemDetail(get(base(ctxA.businessId()) + "/" + UUID.randomUUID() + "/my-interactions",
                ctxB.accessToken()), 403, "ACCESS_DENIED");
    }
}
