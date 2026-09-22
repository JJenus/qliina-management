package com.jjenus.qliina_management.integration;

import com.jjenus.qliina_management.identity.model.User;
import com.jjenus.qliina_management.identity.repository.RoleRepository;
import com.jjenus.qliina_management.identity.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * N-4 — platform-staff management of tenant users: deactivate / reactivate /
 * force password reset behind {@code platform.users.manage} with the same
 * guards as the tenant-side equivalents (last BUSINESS_ADMIN, scope checks,
 * self-targeting). Staff fixtures are created through the regular tenant
 * create-user endpoint with the business owner's token.
 */
class AdminTenantUserIntegrationTest extends BaseIntegrationTest {

    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRepository userRepository;

    private record StaffCtx(UUID userId, String username, String password) {}

    private UUID businessAdminRoleId() {
        return roleRepository.findByName("BUSINESS_ADMIN").orElseThrow().getId();
    }

    private StaffCtx createStaff(AuthContext owner) throws Exception {
        String username = "staff_" + random();
        String password = DEFAULT_PASSWORD;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", username);
        body.put("email", username + "@test.com");
        body.put("phone", "+1" + (555_000_0000L + counter.incrementAndGet()));
        body.put("firstName", "Staff");
        body.put("lastName", "One");
        body.put("password", password);
        body.put("confirmPassword", password);
        body.put("roles", List.of(Map.of(
                "roleId", businessAdminRoleId().toString(),
                "shopId", owner.shopId().toString())));
        String json = post("/api/v1/" + owner.businessId() + "/users", owner.accessToken(), body)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return new StaffCtx(readUuid(json, "$.id"), readString(json, "$.username"), password);
    }

    private ResultActions login(String username, String password) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(Map.of("username", username, "password", password))));
    }

    // ---------------------------------------------------------------------
    // Permission boundary
    // ---------------------------------------------------------------------

    @Test
    void ownerToken_cannotManageTenantUsers() throws Exception {
        AuthContext owner = registerBusinessAndOwner();
        ResultActions rs = patch(
                "/api/v1/admin/businesses/" + owner.businessId() + "/users/" + owner.userId() + "/deactivate",
                owner.accessToken(), Map.of());
        assertProblemDetail(rs, 403, "ACCESS_DENIED");
    }

    // ---------------------------------------------------------------------
    // Deactivate / reactivate
    // ---------------------------------------------------------------------

    @Test
    void deactivateThenReactivateStaff_disablesAndRestoresLogin() throws Exception {
        AuthContext owner = registerBusinessAndOwner();
        StaffCtx staff = createStaff(owner);

        // staff can log in before deactivation
        assertNotNull(loginToken(staff.username(), staff.password()));

        // deactivate
        patch("/api/v1/admin/businesses/" + owner.businessId() + "/users/" + staff.userId() + "/deactivate",
                adminToken(), Map.of())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(staff.userId().toString()))
                .andExpect(jsonPath("$.enabled").value(false));

        // staff login now fails, and the detail view reflects the disabled state
        login(staff.username(), staff.password()).andExpect(status().isUnauthorized());
        get("/api/v1/" + owner.businessId() + "/users/" + staff.userId(), owner.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        // reactivate restores login
        patch("/api/v1/admin/businesses/" + owner.businessId() + "/users/" + staff.userId() + "/reactivate",
                adminToken(), Map.of())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(staff.userId().toString()))
                .andExpect(jsonPath("$.enabled").value(true));
        assertNotNull(loginToken(staff.username(), staff.password()));
    }

    // ---------------------------------------------------------------------
    // Force password reset
    // ---------------------------------------------------------------------

    @Test
    void forcePasswordReset_invalidatesOldPasswordAndReturnsTemporary() throws Exception {
        AuthContext owner = registerBusinessAndOwner();
        StaffCtx staff = createStaff(owner);

        String json = post(
                "/api/v1/admin/businesses/" + owner.businessId() + "/users/" + staff.userId() + "/force-password-reset",
                adminToken(), Map.of())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(staff.userId().toString()))
                .andExpect(jsonPath("$.username").value(staff.username()))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String tempPassword = readString(json, "$.temporaryPassword");
        assertFalse(tempPassword.isBlank());

        // old password is dead, temporary password works
        login(staff.username(), staff.password()).andExpect(status().isUnauthorized());
        assertNotNull(loginToken(staff.username(), tempPassword));
    }

    // ---------------------------------------------------------------------
    // Guard rails
    // ---------------------------------------------------------------------

    @Test
    void cannotDeactivateLastActiveBusinessAdmin() throws Exception {
        AuthContext owner = registerBusinessAndOwner();
        ResultActions rs = patch(
                "/api/v1/admin/businesses/" + owner.businessId() + "/users/" + owner.userId() + "/deactivate",
                adminToken(), Map.of());
        assertProblemDetail(rs, 400, "LAST_ADMIN");
    }

    @Test
    void deactivateUser_fromAnotherBusiness_userNotInBusiness() throws Exception {
        AuthContext businessA = registerBusinessAndOwner();
        AuthContext businessB = registerBusinessAndOwner();

        // target user belongs to business B, but the path claims business A
        ResultActions rs = patch(
                "/api/v1/admin/businesses/" + businessA.businessId() + "/users/" + businessB.userId() + "/deactivate",
                adminToken(), Map.of());
        assertProblemDetail(rs, 400, "USER_NOT_IN_BUSINESS");
    }

    @Test
    void platformAdmin_cannotDeactivateSelf() throws Exception {
        AuthContext owner = registerBusinessAndOwner();
        User admin = userRepository.findByUsername("admin").orElseThrow();
        ResultActions rs = patch(
                "/api/v1/admin/businesses/" + owner.businessId() + "/users/" + admin.getId() + "/deactivate",
                adminToken(), Map.of());
        assertProblemDetail(rs, 400, "SELF_DEACTIVATE");
    }
}