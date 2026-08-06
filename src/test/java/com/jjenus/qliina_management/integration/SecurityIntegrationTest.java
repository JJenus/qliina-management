package com.jjenus.qliina_management.integration;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Date;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
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
                admin, Map.of("status", "SUSPENDED"));
        suspend.andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));
        patch("/api/v1/admin/businesses/" + ctx.businessId() + "/status",
                admin, Map.of("status", "TRIAL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("TRIAL"));
    }
}
