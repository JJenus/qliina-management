package com.jjenus.qliina_management.integration;

import com.jjenus.qliina_management.business.service.ServiceCatalogService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the security contract at the HTTP layer: which endpoints are public,
 * what happens without / with an invalid token (401, empty body), and the
 * permission boundary for platform-admin and cross-tenant access (403).
 */
class SecurityIntegrationTest extends BaseIntegrationTest {

    @Autowired
    @Value("${jwt.secret}")
    private String jwtSecret;

    @Autowired
    private ServiceCatalogService catalogService;

    // ---------------------------------------------------------------------
    // Unauthenticated access
    // ---------------------------------------------------------------------

    @Test
    void protectedEndpoint_withoutToken_returns401EmptyBody() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEmpty());
    }

    @Test
    void protectedEndpoint_withGarbageToken_returns401EmptyBody() throws Exception {
        get("/api/v1/users/me", "not-a-jwt")
                .andExpect(status().isUnauthorized())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEmpty());
    }

    @Test
    void protectedEndpoint_withMalformedAuthHeader_returns401EmptyBody() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/users/me")
                        .header("Authorization", "Basic dXNlcjpwYXNz"))
                .andExpect(status().isUnauthorized())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEmpty());
    }

    @Test
    void protectedEndpoint_withExpiredToken_returns401EmptyBody() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        String expired = Jwts.builder()
                .setSubject(ctx.username())
                .setIssuedAt(new Date(System.currentTimeMillis() - 60_000L))
                .setExpiration(new Date(System.currentTimeMillis() - 30_000L))
                .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes()), SignatureAlgorithm.HS256)
                .compact();
        get("/api/v1/users/me", expired)
                .andExpect(status().isUnauthorized())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEmpty());
    }

    // ---------------------------------------------------------------------
    // Whitelisted (public) endpoints
    // ---------------------------------------------------------------------

    @Test
    void publicOrderTracking_withoutToken_notBlocked() throws Exception {
        // No token, but the request must reach the handler (404/400) — not 401.
        get("/api/v1/public/orders/track/NOPE-123", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("ORDER_NOT_FOUND"));
    }

    @Test
    void authEndpoint_withoutToken_allowed() throws Exception {
        post("/api/v1/auth/forgot-password", null, Map.of("username", "no_such_user_x"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void actuatorHealth_withoutToken_allowed() throws Exception {
        get("/actuator/health", null)
                .andExpect(status().isOk());
    }

    @Test
    void swaggerDocs_withoutToken_allowed() throws Exception {
        get("/v3/api-docs", null)
                .andExpect(status().isOk());
    }

    // ---------------------------------------------------------------------
    // Authenticated access
    // ---------------------------------------------------------------------

    @Test
    void validToken_allowsAccessToProtectedEndpoint() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        get("/api/v1/users/me", ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(ctx.username()))
                .andExpect(jsonPath("$.businessId").value(ctx.businessId().toString()));
    }

    @Test
    void tenantAdmin_canAccessOwnBusiness() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        get("/api/v1/businesses/" + ctx.businessId(), ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ctx.businessId().toString()));
    }

    // ---------------------------------------------------------------------
    // Tenant isolation / platform admin boundary
    // ---------------------------------------------------------------------

    @Test
    void tenantAdmin_cannotListPlatformBusinesses() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertProblemDetail(get("/api/v1/businesses", ctx.accessToken()), 403, "ACCESS_DENIED");
    }

    @Test
    void tenantAdmin_cannotManagePlatformUsers() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertProblemDetail(get("/api/v1/admin/platform-users", ctx.accessToken()), 403, "ACCESS_DENIED");
    }

    @Test
    void tenantAdmin_cannotChangeBusinessStatusViaAdminEndpoint() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertProblemDetail(patch("/api/v1/admin/businesses/" + ctx.businessId() + "/status",
                ctx.accessToken(), Map.of("status", "SUSPENDED")), 403, "ACCESS_DENIED");
    }

    @Test
    void tenantA_cannotReadTenantB_Business() throws Exception {
        AuthContext a = registerBusinessAndOwner();
        AuthContext b = registerBusinessAndOwner();
        // Tenant A may not read tenant B's business metadata.
        assertProblemDetail(get("/api/v1/businesses/" + b.businessId(), a.accessToken()), 403, "ACCESS_DENIED");
    }

    @Test
    void superAdmin_canAccessPlatformAdminEndpoints() throws Exception {
        String admin = adminToken();
        get("/api/v1/admin/platform-users", admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void superAdmin_canListBusinesses() throws Exception {
        registerBusinessAndOwner();
        get("/api/v1/businesses", adminToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void superAdmin_canSuspendAndReactivateBusiness() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        String admin = adminToken();
        ResultActions suspend = patch("/api/v1/admin/businesses/" + ctx.businessId() + "/status",
                admin, Map.of("status", "SUSPENDED", "reason", "Compliance review"));
        suspend.andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));
        patch("/api/v1/admin/businesses/" + ctx.businessId() + "/status",
                admin, Map.of("status", "ACTIVE", "reason", "Issues resolved"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void superAdmin_suspendingOrCancellingRequiresReason() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        String admin = adminToken();
        ResultActions suspend = patch("/api/v1/admin/businesses/" + ctx.businessId() + "/status",
                admin, Map.of("status", "SUSPENDED"));
        assertProblemDetail(suspend, 400, "VALIDATION_ERROR");
        suspend.andExpect(jsonPath("$.field").value("reason"));
        assertProblemDetail(patch("/api/v1/admin/businesses/" + ctx.businessId() + "/status",
                admin, Map.of("status", "CANCELLED")), 400, "VALIDATION_ERROR");
    }

    @Test
    void superAdmin_cannotTransitionOutOfCancelled() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        String admin = adminToken();
        patch("/api/v1/admin/businesses/" + ctx.businessId() + "/status",
                admin, Map.of("status", "CANCELLED", "reason", "Business closed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        // CANCELLED is terminal — the same state is a no-op, anything else is rejected.
        patch("/api/v1/admin/businesses/" + ctx.businessId() + "/status",
                admin, Map.of("status", "CANCELLED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        assertProblemDetail(patch("/api/v1/admin/businesses/" + ctx.businessId() + "/status",
                admin, Map.of("status", "ACTIVE")), 400, "INVALID_STATUS_TRANSITION");
    }

    @Test
    void deactivatedUsersExistingToken_isRejected() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        String uname = "deact_" + random();
        Map<String, Object> body = new HashMap<>();
        body.put("username", uname);
        body.put("email", uname + "@test.com");
        body.put("phone", "+1" + (555_700_0000L + counter.incrementAndGet()));
        body.put("firstName", "Soon");
        body.put("lastName", "Gone");
        body.put("password", DEFAULT_PASSWORD);
        body.put("confirmPassword", DEFAULT_PASSWORD);
        MvcResult res = post("/api/v1/" + ctx.businessId() + "/users", ctx.accessToken(), body)
                .andExpect(status().isOk()).andReturn();
        UUID userId = extractUuid(res, "$.id");

        String staffToken = loginToken(uname, DEFAULT_PASSWORD);
        get("/api/v1/users/me", staffToken).andExpect(status().isOk());

        assertSuccess(delete("/api/v1/" + ctx.businessId() + "/users/" + userId, ctx.accessToken()),
                "User deactivated successfully");

        // The token minted before deactivation must no longer authenticate.
        get("/api/v1/users/me", staffToken).andExpect(status().isUnauthorized());

        // And re-activating restores it (picker: token is re-minted on login).
        assertSuccess(patch("/api/v1/" + ctx.businessId() + "/users/" + userId + "/activate",
                ctx.accessToken(), null), "User activated successfully");
        get("/api/v1/users/me", loginToken(uname, DEFAULT_PASSWORD)).andExpect(status().isOk());
    }

    // ---------------------------------------------------------------------
    // Cross-tenant IDOR on payment resources (remediation C3)
    // ---------------------------------------------------------------------

    @Test
    void tenantA_cannotReadTenantB_Payment() throws Exception {
        AuthContext a = registerBusinessAndOwner();
        AuthContext b = registerBusinessAndOwner();
        UUID paymentId = createPaymentFor(b);
        // Tenant A must not read tenant B's payment detail via B's business id.
        assertProblemDetail(get("/api/v1/" + b.businessId() + "/payments/" + paymentId, a.accessToken()),
                403, "ACCESS_DENIED");
    }

    // ---------------------------------------------------------------------
    // Security headers + CORS (remediation C4 / C5)
    // ---------------------------------------------------------------------

    @Test
    void protectedResponse_shipsSecurityHeaders() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        get("/api/v1/users/me", ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy",
                        "default-src 'self'; base-uri 'self'; frame-ancestors 'none'; form-action 'self'"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    void cors_disallowedOrigin_isRejected() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        // Evil.com is not in app.cors.allowed-origins — the credentialed
        // request must be rejected, not silently proxied.
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + ctx.accessToken())
                        .header("Origin", "https://evil.example.com"))
                .andExpect(status().isForbidden());
    }

    @Test
    void cors_allowedOrigin_roundTrips() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        // localhost:3000 IS in app.cors.allowed-origins (test profile).
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + ctx.accessToken())
                        .header("Origin", "http://localhost:3000"))
                .andExpect(status().isOk());
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    /** Creates a provider payment in tenant's business and returns its id. */
    private UUID createPaymentFor(AuthContext ctx) throws Exception {
        // Simplest deterministic money path: create a customer + order, then
        // process a manual CASH payment with no provider (COMPLETED immediately).
        String phone = "+1" + (555_900_0000L + counter.incrementAndGet());
        Map<String, Object> custBody = Map.of(
                "firstName", "Cross", "lastName", "Tenant", "phone", phone);
        String custJson = post("/api/v1/" + ctx.businessId() + "/customers", ctx.accessToken(), custBody)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        UUID customerId = readUuid(custJson, "$.id");

        UUID serviceTypeId = catalogService.getActiveServices(ctx.businessId()).get(0).getId();
        UUID garmentTypeId = catalogService.getActiveGarments(ctx.businessId()).get(0).getId();
        Map<String, Object> orderBody = Map.of(
                "customerId", customerId.toString(),
                "shopId", ctx.shopId().toString(),
                "items", List.of(Map.of(
                        "serviceTypeId", serviceTypeId.toString(),
                        "garmentTypeId", garmentTypeId.toString(),
                        "quantity", 2,
                        "unitPrice", 3.50,
                        "description", "Two shirts")));
        String orderJson = post("/api/v1/" + ctx.businessId() + "/orders", ctx.accessToken(), orderBody)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        UUID orderId = readUuid(orderJson, "$.id");

        String payJson = post("/api/v1/" + ctx.businessId() + "/payments/orders/" + orderId + "/process",
                        ctx.accessToken(), Map.of("amount", 7.0, "method", "CASH"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return readUuid(payJson, "$.paymentId");
    }
}
