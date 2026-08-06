package com.jjenus.qliina_management.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the @RequireClockIn aspect: operational staff (WASHER / IRONER /
 * DELIVERY / FRONT_DESK) must be clocked in before any non-read request to the
 * gated modules (orders, customers, payments, inventory, expenses, quality),
 * while reads and privileged roles (e.g. BUSINESS_ADMIN) bypass the gate.
 */
class ClockInGateIntegrationTest extends BaseIntegrationTest {

    /** Creates a WASHER user in the business and logs in, returning their token. */
    private String createWasher(AuthContext ctx) throws Exception {
        MvcResult rolesRes = get("/api/v1/" + ctx.businessId() + "/users/available-roles", ctx.accessToken())
                .andExpect(status().isOk())
                .andReturn();
        String rolesJson = rolesRes.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        List<String> names = JsonPath.read(rolesJson, "$[*].name");
        List<String> ids = JsonPath.read(rolesJson, "$[*].id");
        int idx = names.indexOf("WASHER");
        if (idx < 0) throw new IllegalStateException("WASHER role not found");

        String unique = random();
        Map<String, Object> body = new java.util.HashMap<>();
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

        return loginToken("washer_" + unique, DEFAULT_PASSWORD);
    }

    private Map<String, Object> statusBody(String status) {
        return Map.of("status", status);
    }

    @Test
    void washer_readRequest_bypassesClockInGate() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        String washer = createWasher(ctx);
        // GET is a read method — the gate must NOT apply.
        get("/api/v1/" + ctx.businessId() + "/orders", washer)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void washer_writeRequest_withoutClockIn_returnsNotClockedIn() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        String washer = createWasher(ctx);
        UUID orderId = UUID.randomUUID();
        // Gate fires before any service logic, so the bogus order id never matters.
        assertProblemDetail(post("/api/v1/" + ctx.businessId() + "/orders/" + orderId + "/status",
                washer, statusBody("RECEIVED")), 400, "NOT_CLOCKED_IN");
    }

    @Test
    void washer_writeRequest_afterClockIn_passesGate() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        String washer = createWasher(ctx);

        post("/api/v1/" + ctx.businessId() + "/employees/clock-in", washer,
                Map.of("shopId", ctx.shopId().toString()))
                .andExpect(status().isOk());

        UUID orderId = UUID.randomUUID();
        // Gate now passes; the request proceeds to the service and hits the bogus order.
        assertProblemDetail(post("/api/v1/" + ctx.businessId() + "/orders/" + orderId + "/status",
                washer, statusBody("RECEIVED")), 400, "ORDER_NOT_FOUND");
    }

    @Test
    void washer_doubleClockIn_returnsActiveShiftExists() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        String washer = createWasher(ctx);
        Map<String, Object> body = Map.of("shopId", ctx.shopId().toString());
        post("/api/v1/" + ctx.businessId() + "/employees/clock-in", washer, body)
                .andExpect(status().isOk());
        assertProblemDetail(post("/api/v1/" + ctx.businessId() + "/employees/clock-in", washer, body),
                400, "ACTIVE_SHIFT_EXISTS");
    }

    @Test
    void businessAdmin_writeRequest_bypassesClockInGate() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        UUID orderId = UUID.randomUUID();
        // BUSINESS_ADMIN is not in the clock-required roles — gate must not fire.
        assertProblemDetail(post("/api/v1/" + ctx.businessId() + "/orders/" + orderId + "/status",
                ctx.accessToken(), statusBody("RECEIVED")), 400, "ORDER_NOT_FOUND");
    }

    @Test
    void washer_clockOut_endsShiftAndReEnablesGate() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        String washer = createWasher(ctx);
        Map<String, Object> body = Map.of("shopId", ctx.shopId().toString());

        post("/api/v1/" + ctx.businessId() + "/employees/clock-in", washer, body)
                .andExpect(status().isOk());
        UUID orderId = UUID.randomUUID();
        assertProblemDetail(post("/api/v1/" + ctx.businessId() + "/orders/" + orderId + "/status",
                washer, statusBody("RECEIVED")), 400, "ORDER_NOT_FOUND");

        post("/api/v1/" + ctx.businessId() + "/employees/clock-out", washer, Map.of())
                .andExpect(status().isOk());

        // After clock-out the gate is active again.
        assertProblemDetail(post("/api/v1/" + ctx.businessId() + "/orders/" + orderId + "/status",
                washer, statusBody("RECEIVED")), 400, "NOT_CLOCKED_IN");
    }
}
