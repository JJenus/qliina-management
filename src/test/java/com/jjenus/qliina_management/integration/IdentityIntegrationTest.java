package com.jjenus.qliina_management.integration;

import com.jjenus.qliina_management.identity.repository.PermissionRepository;
import com.jjenus.qliina_management.identity.repository.RoleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Identity suite: ProfileController, UserController, AdminPlatformUserController,
 * BusinessConfigController.
 */
class IdentityIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    // ---------------------------------------------------------------------
    // ProfileController — /api/v1/users
    // ---------------------------------------------------------------------

    @Test
    void getMe_returnsCurrentUser() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        get("/api/v1/users/me", ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(ctx.username()))
                .andExpect(jsonPath("$.email").value(ctx.email()))
                .andExpect(jsonPath("$.businessId").value(ctx.businessId().toString()))
                .andExpect(jsonPath("$.currentShopId").value(ctx.shopId().toString()))
                .andExpect(jsonPath("$.roles[0]").value("BUSINESS_ADMIN"))
                .andExpect(jsonPath("$.requires2FA").value(false))
                .andExpect(jsonPath("$.permissions").isArray());
    }

    @Test
    void getMe_missingToken_unauthorized() throws Exception {
        get("/api/v1/users/me", null).andExpect(status().isUnauthorized());
    }

    @Test
    void getMe_garbageToken_unauthorized() throws Exception {
        get("/api/v1/users/me", "not-a-real-jwt").andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------------
    // UserController — /api/v1/{businessId}/users
    // ---------------------------------------------------------------------

    @Test
    void listUsers_returnsOwnerAndSearch() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        get("/api/v1/" + ctx.businessId() + "/users", ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].username").value(ctx.username()));

        get("/api/v1/" + ctx.businessId() + "/users?search=" + ctx.username(), ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].username").value(ctx.username()));
    }

    @Test
    void listUsers_requiresAuth() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        get("/api/v1/" + ctx.businessId() + "/users", null).andExpect(status().isUnauthorized());
    }

    @Test
    void listUsers_crossTenantDenied() throws Exception {
        AuthContext a = registerBusinessAndOwner();
        AuthContext b = registerBusinessAndOwner();
        get("/api/v1/" + b.businessId() + "/users", a.accessToken())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    private UUID shopManagerRoleId() {
        return roleRepository.findByName("SHOP_MANAGER").orElseThrow().getId();
    }

    private Map<String, Object> createUserBody(String username, String email, String phone,
                                               String password, String confirm) {
        Map<String, Object> body = new HashMap<>();
        body.put("username", username);
        body.put("email", email);
        body.put("phone", phone);
        body.put("firstName", "New");
        body.put("lastName", "Employee");
        body.put("password", password);
        body.put("confirmPassword", confirm);
        return body;
    }

    private String newPhone() {
        return "+1" + (555_400_0000L + counter.incrementAndGet());
    }

    @Test
    void createUser_success_withRoleAndLogin() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        String uname = "staff_" + random();
        Map<String, Object> roles = Map.of("roleId", shopManagerRoleId().toString(), "shopId", ctx.shopId().toString());
        Map<String, Object> body = createUserBody(uname, uname + "@test.com", newPhone(),
                DEFAULT_PASSWORD, DEFAULT_PASSWORD);
        body.put("roles", List.of(roles));

        post("/api/v1/" + ctx.businessId() + "/users", ctx.accessToken(), body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(uname))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.roles[0]").value("SHOP_MANAGER"));

        // The new user can log in and sees their role in /users/me.
        String token = loginToken(uname, DEFAULT_PASSWORD);
        get("/api/v1/users/me", token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(uname))
                .andExpect(jsonPath("$.roles[0]").value("SHOP_MANAGER"));
    }

    @Test
    void createUser_duplicateUsername() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        Map<String, Object> body = createUserBody("dup_" + random(), "a" + random() + "@test.com", newPhone(),
                DEFAULT_PASSWORD, DEFAULT_PASSWORD);
        post("/api/v1/" + ctx.businessId() + "/users", ctx.accessToken(), body).andExpect(status().isOk());

        Map<String, Object> dup = createUserBody((String) body.get("username"), "b" + random() + "@test.com",
                newPhone(), DEFAULT_PASSWORD, DEFAULT_PASSWORD);
        assertProblemDetail(post("/api/v1/" + ctx.businessId() + "/users", ctx.accessToken(), dup),
                400, "USERNAME_EXISTS");
    }

    @Test
    void createUser_duplicateEmail() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        Map<String, Object> body = createUserBody("u1_" + random(), "share" + random() + "@test.com", newPhone(),
                DEFAULT_PASSWORD, DEFAULT_PASSWORD);
        post("/api/v1/" + ctx.businessId() + "/users", ctx.accessToken(), body).andExpect(status().isOk());

        Map<String, Object> dup = createUserBody("u2_" + random(), (String) body.get("email"), newPhone(),
                DEFAULT_PASSWORD, DEFAULT_PASSWORD);
        assertProblemDetail(post("/api/v1/" + ctx.businessId() + "/users", ctx.accessToken(), dup),
                400, "EMAIL_EXISTS");
    }

    @Test
    void createUser_duplicatePhone() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        Map<String, Object> body = createUserBody("u1_" + random(), "a" + random() + "@test.com", newPhone(),
                DEFAULT_PASSWORD, DEFAULT_PASSWORD);
        post("/api/v1/" + ctx.businessId() + "/users", ctx.accessToken(), body).andExpect(status().isOk());

        Map<String, Object> dup = createUserBody("u2_" + random(), "b" + random() + "@test.com",
                (String) body.get("phone"), DEFAULT_PASSWORD, DEFAULT_PASSWORD);
        assertProblemDetail(post("/api/v1/" + ctx.businessId() + "/users", ctx.accessToken(), dup),
                400, "PHONE_EXISTS");
    }

    @Test
    void createUser_passwordMismatch() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        Map<String, Object> body = createUserBody("pm_" + random(), "a" + random() + "@test.com", newPhone(),
                DEFAULT_PASSWORD, "Different!1");
        ResultActions rs = post("/api/v1/" + ctx.businessId() + "/users", ctx.accessToken(), body);
        assertProblemDetail(rs, 400, "PASSWORD_MISMATCH");
        rs.andExpect(jsonPath("$.field").value("confirmPassword"));
    }

    @Test
    void createUser_validation_blankRequiredFields() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertValidation(post("/api/v1/" + ctx.businessId() + "/users", ctx.accessToken(), Map.of()),
                Map.of(
                        "username", "Username is required",
                        "phone", "Phone is required",
                        "firstName", "First name is required",
                        "lastName", "Last name is required",
                        "password", "Password is required"));
    }

    @Test
    void createUser_invalidPhone_validation() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        Map<String, Object> body = createUserBody("ip_" + random(), "a" + random() + "@test.com", "not-a-phone",
                DEFAULT_PASSWORD, DEFAULT_PASSWORD);
        assertValidation(post("/api/v1/" + ctx.businessId() + "/users", ctx.accessToken(), body),
                Map.of("phone", "Invalid phone number format"));
    }

    @Test
    void createUser_planLimitExceeded() throws Exception {
        // During the trial all features are enabled, so the FREE plan limit
        // (maxUsers=3) only applies once the trial has expired; the owner counts
        // as 1, so only 2 more users fit.
        AuthContext ctx = registerBusinessAndOwner();
        expireTrial(ctx.businessId());
        for (int i = 0; i < 2; i++) {
            Map<String, Object> body = createUserBody("pl" + i + "_" + random(), "a" + random() + "@test.com",
                    newPhone(), DEFAULT_PASSWORD, DEFAULT_PASSWORD);
            post("/api/v1/" + ctx.businessId() + "/users", ctx.accessToken(), body).andExpect(status().isOk());
        }
        Map<String, Object> third = createUserBody("pl2_" + random(), "a" + random() + "@test.com",
                newPhone(), DEFAULT_PASSWORD, DEFAULT_PASSWORD);
        assertProblemDetail(post("/api/v1/" + ctx.businessId() + "/users", ctx.accessToken(), third),
                400, "PLAN_LIMIT_EXCEEDED");
    }

    @Test
    void getUser_notFound() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertProblemDetail(get("/api/v1/" + ctx.businessId() + "/users/" + UUID.randomUUID(), ctx.accessToken()),
                400, "USER_NOT_FOUND");
    }

    @Test
    void getUser_crossTenantNotExposed() throws Exception {
        AuthContext a = registerBusinessAndOwner();
        AuthContext b = registerBusinessAndOwner();
        get("/api/v1/" + a.businessId() + "/users/" + a.userId(), b.accessToken())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    void updateUser_updatesFields() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        Map<String, Object> body = createUserBody("up_" + random(), "a" + random() + "@test.com", newPhone(),
                DEFAULT_PASSWORD, DEFAULT_PASSWORD);
        MvcResult res = post("/api/v1/" + ctx.businessId() + "/users", ctx.accessToken(), body)
                .andExpect(status().isOk()).andReturn();
        UUID userId = extractUuid(res, "$.id");

        put("/api/v1/" + ctx.businessId() + "/users/" + userId, ctx.accessToken(),
                Map.of("firstName", "Renamed", "lastName", "Staff"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Renamed"))
                .andExpect(jsonPath("$.lastName").value("Staff"));
    }

    @Test
    void updateUser_duplicateEmail() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        Map<String, Object> body = createUserBody("ue_" + random(), "a" + random() + "@test.com", newPhone(),
                DEFAULT_PASSWORD, DEFAULT_PASSWORD);
        MvcResult res = post("/api/v1/" + ctx.businessId() + "/users", ctx.accessToken(), body)
                .andExpect(status().isOk()).andReturn();
        UUID userId = extractUuid(res, "$.id");

        // Try to set the user's email to the owner's email.
        assertProblemDetail(put("/api/v1/" + ctx.businessId() + "/users/" + userId, ctx.accessToken(),
                Map.of("email", ctx.email())), 400, "EMAIL_EXISTS");
    }

    @Test
    void updateUser_invalidEmail_validation() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertValidation(put("/api/v1/" + ctx.businessId() + "/users/" + ctx.userId(), ctx.accessToken(),
                Map.of("email", "not-an-email")), Map.of("email", "Invalid email format"));
    }

    @Test
    void deactivateAndReactivateUser() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        Map<String, Object> body = createUserBody("da_" + random(), "a" + random() + "@test.com", newPhone(),
                DEFAULT_PASSWORD, DEFAULT_PASSWORD);
        MvcResult res = post("/api/v1/" + ctx.businessId() + "/users", ctx.accessToken(), body)
                .andExpect(status().isOk()).andReturn();
        UUID userId = extractUuid(res, "$.id");

        assertSuccess(delete("/api/v1/" + ctx.businessId() + "/users/" + userId, ctx.accessToken()),
                "User deactivated successfully");
        get("/api/v1/" + ctx.businessId() + "/users/" + userId, ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        assertSuccess(patch("/api/v1/" + ctx.businessId() + "/users/" + userId + "/activate", ctx.accessToken(), null),
                "User activated successfully");
        get("/api/v1/" + ctx.businessId() + "/users/" + userId, ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));
    }

    @Test
    void assignRoles_thenGetPermissions() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        Map<String, Object> body = createUserBody("ar_" + random(), "a" + random() + "@test.com", newPhone(),
                DEFAULT_PASSWORD, DEFAULT_PASSWORD);
        MvcResult res = post("/api/v1/" + ctx.businessId() + "/users", ctx.accessToken(), body)
                .andExpect(status().isOk()).andReturn();
        UUID userId = extractUuid(res, "$.id");

        assertSuccess(post("/api/v1/" + ctx.businessId() + "/users/" + userId + "/roles", ctx.accessToken(),
                Map.of("roles", List.of(Map.of("roleId", shopManagerRoleId().toString(), "shopId", ctx.shopId().toString())))),
                "Roles assigned successfully");

        get("/api/v1/" + ctx.businessId() + "/users/" + userId + "/permissions", ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rolePermissions[0].role").value("SHOP_MANAGER"))
                .andExpect(jsonPath("$.effectivePermissions", hasItem("order.view")));
    }

    @Test
    void removeRole() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        Map<String, Object> body = createUserBody("rr_" + random(), "a" + random() + "@test.com", newPhone(),
                DEFAULT_PASSWORD, DEFAULT_PASSWORD);
        MvcResult res = post("/api/v1/" + ctx.businessId() + "/users", ctx.accessToken(), body)
                .andExpect(status().isOk()).andReturn();
        UUID userId = extractUuid(res, "$.id");

        UUID roleId = shopManagerRoleId();
        post("/api/v1/" + ctx.businessId() + "/users/" + userId + "/roles", ctx.accessToken(),
                Map.of("roles", List.of(Map.of("roleId", roleId.toString(), "shopId", ctx.shopId().toString()))))
                .andExpect(status().isOk());

        assertSuccess(delete("/api/v1/" + ctx.businessId() + "/users/" + userId + "/roles/" + roleId, ctx.accessToken()),
                "Role removed successfully");
        get("/api/v1/" + ctx.businessId() + "/users/" + userId + "/permissions", ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rolePermissions").isArray())
                .andExpect(jsonPath("$.rolePermissions").isEmpty());
    }

    @Test
    void grantAndRevokePermission() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        Map<String, Object> body = createUserBody("gp_" + random(), "a" + random() + "@test.com", newPhone(),
                DEFAULT_PASSWORD, DEFAULT_PASSWORD);
        MvcResult res = post("/api/v1/" + ctx.businessId() + "/users", ctx.accessToken(), body)
                .andExpect(status().isOk()).andReturn();
        UUID userId = extractUuid(res, "$.id");

        UUID permId = permissionRepository.findByName("order.view").orElseThrow().getId();
        assertSuccess(post("/api/v1/" + ctx.businessId() + "/users/" + userId + "/permissions", ctx.accessToken(),
                Map.of("permissionId", permId.toString())), "Permission granted successfully");

        get("/api/v1/" + ctx.businessId() + "/users/" + userId + "/permissions", ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.directPermissions", hasItem("order.view")));

        assertSuccess(delete("/api/v1/" + ctx.businessId() + "/users/" + userId + "/permissions/" + permId,
                ctx.accessToken()), "Permission revoked successfully");
        get("/api/v1/" + ctx.businessId() + "/users/" + userId + "/permissions", ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.directPermissions").isEmpty());
    }

    @Test
    void assignShops_andGetUserShops() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        Map<String, Object> body = createUserBody("as_" + random(), "a" + random() + "@test.com", newPhone(),
                DEFAULT_PASSWORD, DEFAULT_PASSWORD);
        MvcResult res = post("/api/v1/" + ctx.businessId() + "/users", ctx.accessToken(), body)
                .andExpect(status().isOk()).andReturn();
        UUID userId = extractUuid(res, "$.id");

        assertSuccess(post("/api/v1/" + ctx.businessId() + "/users/" + userId + "/shops", ctx.accessToken(),
                Map.of("shopAssignments", List.of(Map.of(
                        "shopId", ctx.shopId().toString(),
                        "isPrimary", true,
                        "roles", List.of(shopManagerRoleId().toString()))))),
                "Shops assigned successfully");

        get("/api/v1/" + ctx.businessId() + "/users/" + userId + "/shops", ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].shopId").value(ctx.shopId().toString()))
                .andExpect(jsonPath("$[0].shopName").value("Main Shop"))
                .andExpect(jsonPath("$[0].isPrimary").value(true))
                .andExpect(jsonPath("$[0].roles[0].roleName").value("SHOP_MANAGER"));
    }

    @Test
    void removeFromShop() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        Map<String, Object> body = createUserBody("rs_" + random(), "a" + random() + "@test.com", newPhone(),
                DEFAULT_PASSWORD, DEFAULT_PASSWORD);
        MvcResult res = post("/api/v1/" + ctx.businessId() + "/users", ctx.accessToken(), body)
                .andExpect(status().isOk()).andReturn();
        UUID userId = extractUuid(res, "$.id");

        post("/api/v1/" + ctx.businessId() + "/users/" + userId + "/shops", ctx.accessToken(),
                Map.of("shopAssignments", List.of(Map.of("shopId", ctx.shopId().toString(), "isPrimary", true))))
                .andExpect(status().isOk());

        assertSuccess(delete("/api/v1/" + ctx.businessId() + "/users/" + userId + "/shops/" + ctx.shopId(),
                ctx.accessToken()), "User removed from shop successfully");
        get("/api/v1/" + ctx.businessId() + "/users/" + userId + "/shops", ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void availableRoles_containsBusinessAndPlatformRoles() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        get("/api/v1/" + ctx.businessId() + "/users/available-roles", ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItems("BUSINESS_ADMIN", "SHOP_MANAGER")));
    }

    @Test
    void availableRoles_crossTenantDenied() throws Exception {
        AuthContext a = registerBusinessAndOwner();
        AuthContext b = registerBusinessAndOwner();
        get("/api/v1/" + b.businessId() + "/users/available-roles", a.accessToken())
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------------
    // AdminPlatformUserController — /api/v1/admin/platform-users
    // ---------------------------------------------------------------------

    @Test
    void listPlatformUsers_superAdmin() throws Exception {
        get("/api/v1/admin/platform-users", adminToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].username").exists());
    }

    @Test
    void listPlatformUsers_tenantDenied() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        get("/api/v1/admin/platform-users", ctx.accessToken())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    void listPlatformUsers_invalidRoleFilter() throws Exception {
        assertProblemDetail(get("/api/v1/admin/platform-users?role=NOPE", adminToken()), 400, "INVALID_ROLE");
    }

    private Map<String, Object> inviteBody(String username) {
        Map<String, Object> body = new HashMap<>();
        body.put("username", username);
        body.put("email", username + "@test.com");
        body.put("firstName", "Platform");
        body.put("lastName", "Staff");
        body.put("role", "SUPPORT_AGENT");
        body.put("temporaryPassword", "Temp@12345");
        return body;
    }

    @Test
    void invitePlatformUser_createsAndLogsIn() throws Exception {
        String uname = "support_" + random();
        MvcResult res = post("/api/v1/admin/platform-users", adminToken(), inviteBody(uname))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(uname))
                .andExpect(jsonPath("$.roles[0]").value("SUPPORT_AGENT"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andReturn();

        // Invited platform user can log in with the temporary password.
        String token = loginToken(uname, "Temp@12345");
        get("/api/v1/users/me", token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("SUPPORT_AGENT"));
    }

    @Test
    void supportAgent_searchAndTenantUsers_maskPii() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        // A real staff member with identifiable PII.
        String staffUser = "mask_" + random();
        String staffEmail = "maskme_" + random() + "@test.com";
        post("/api/v1/" + ctx.businessId() + "/users", ctx.accessToken(),
                createUserBody(staffUser, staffEmail, newPhone(), DEFAULT_PASSWORD, DEFAULT_PASSWORD))
                .andExpect(status().isOk());

        String uname = "sagent_" + random();
        post("/api/v1/admin/platform-users", adminToken(), inviteBody(uname))
                .andExpect(status().isOk());
        String agent = loginToken(uname, "Temp@12345");

        // SUPPORT_AGENT sees masked PII in global search... (m***@t***.com / N*** E***)
        get("/api/v1/admin/search?q=" + staffUser, agent)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users[0].username").value(staffUser))
                .andExpect(jsonPath("$.users[0].email").value("m***@t***.com"))
                .andExpect(jsonPath("$.users[0].firstName").value("N***"))
                .andExpect(jsonPath("$.users[0].lastName").value("E***"))
                .andExpect(jsonPath("$.users[0].enabled").value(true));
        // ...and in the business tenant list.
        get("/api/v1/admin/businesses/" + ctx.businessId() + "/users", agent)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.username=='" + staffUser + "')].email").value("m***@t***.com"))
                .andExpect(jsonPath("$[?(@.username=='" + staffUser + "')].firstName").value("N***"));

        // Platform admins / support supervisors still see full PII.
        get("/api/v1/admin/businesses/" + ctx.businessId() + "/users", adminToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.username=='" + staffUser + "')].email").value(staffEmail));
    }

    @Test
    void invitePlatformUser_invalidRole() throws Exception {
        Map<String, Object> body = inviteBody("inv_" + random());
        body.put("role", "SUPER_ADMIN");
        assertProblemDetail(post("/api/v1/admin/platform-users", adminToken(), body),
                400, "INVALID_ROLE");
    }

    @Test
    void invitePlatformUser_duplicateUsername() throws Exception {
        String uname = "dup_" + random();
        post("/api/v1/admin/platform-users", adminToken(), inviteBody(uname)).andExpect(status().isOk());
        Map<String, Object> dup = inviteBody(uname);
        dup.put("email", "other" + random() + "@test.com");
        assertProblemDetail(post("/api/v1/admin/platform-users", adminToken(), dup), 400, "USERNAME_EXISTS");
    }

    @Test
    void invitePlatformUser_validation() throws Exception {
        assertValidation(post("/api/v1/admin/platform-users", adminToken(), Map.of()),
                Map.of(
                        "username", "must not be blank",
                        "email", "must not be blank",
                        "firstName", "must not be blank",
                        "lastName", "must not be blank",
                        "role", "must not be blank"));
    }

    @Test
    void deactivatePlatformUser_andReflectsInList() throws Exception {
        String uname = "deact_" + random();
        MvcResult res = post("/api/v1/admin/platform-users", adminToken(), inviteBody(uname))
                .andExpect(status().isOk()).andReturn();
        UUID userId = extractUuid(res, "$.id");

        patch("/api/v1/admin/platform-users/" + userId + "/deactivate", adminToken(), null)
                .andExpect(status().isNoContent());

        get("/api/v1/admin/platform-users?role=SUPPORT_AGENT", adminToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id=='" + userId + "')].enabled").value(false));
    }

    @Test
    void deactivatePlatformUser_superAdminForbidden() throws Exception {
        String me = get("/api/v1/users/me", adminToken())
                .andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString();
        UUID adminId = readUuid(me, "$.id");

        assertProblemDetail(patch("/api/v1/admin/platform-users/" + adminId + "/deactivate", adminToken(), null),
                400, "FORBIDDEN_OPERATION");
    }

    @Test
    void deactivatePlatformUser_notFound() throws Exception {
        assertProblemDetail(patch("/api/v1/admin/platform-users/" + UUID.randomUUID() + "/deactivate",
                adminToken(), null), 400, "USER_NOT_FOUND");
    }

    // ---------------------------------------------------------------------
    // BusinessConfigController — /api/v1/{businessId}/config
    // ---------------------------------------------------------------------

    @Test
    void getConfig_returnsDefaults() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        get("/api/v1/" + ctx.businessId() + "/config", ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("NGN"))
                .andExpect(jsonPath("$.currencySymbol").value("₦"))
                .andExpect(jsonPath("$.timezone").value("Africa/Lagos"))
                .andExpect(jsonPath("$.dateFormat").value("dd/MM/yyyy"))
                .andExpect(jsonPath("$.receiptPrefix").value("RCPT"))
                .andExpect(jsonPath("$.notificationSettings.emailNotifications").value(true))
                .andExpect(jsonPath("$.notificationSettings.reminderBeforeDue").value(24));
    }

    private Map<String, Object> fullConfigBody() {
        Map<String, Object> body = new HashMap<>();
        body.put("taxRate", 7.5);
        body.put("currency", "USD");
        body.put("currencySymbol", "$");
        body.put("currencyLocale", "en-US");
        body.put("timezone", "America/New_York");
        body.put("dateFormat", "MM/dd/yyyy");
        body.put("receiptPrefix", "RCPT2");
        body.put("invoicePrefix", "INV2");
        body.put("orderPrefix", "ORD2");
        body.put("loyaltyPointsPerDollar", 20);
        body.put("minRedeemablePoints", 200);
        body.put("autoArchiveDays", 30);
        body.put("allowNegativeInventory", true);
        body.put("requireQualityCheck", false);
        body.put("idleTimeoutMinutes", 60);
        body.put("dayCutoffTime", "01:00");
        body.put("defaultShiftHours", 9);
        body.put("hourlyRate", 20.0);
        body.put("requireClockInRoles", "WASHER");
        Map<String, Object> notif = new HashMap<>();
        notif.put("emailNotifications", false);
        notif.put("smsNotifications", true);
        notif.put("whatsappNotifications", true);
        notif.put("orderConfirmation", false);
        notif.put("orderReady", true);
        notif.put("paymentReceipt", false);
        notif.put("reminderBeforeDue", 12);
        body.put("notificationSettings", notif);
        return body;
    }

    @Test
    void updateConfig_updatesFields() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        put("/api/v1/" + ctx.businessId() + "/config", ctx.accessToken(), fullConfigBody())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.timezone").value("America/New_York"))
                .andExpect(jsonPath("$.dateFormat").value("MM/dd/yyyy"))
                .andExpect(jsonPath("$.hourlyRate").value(20.0))
                .andExpect(jsonPath("$.notificationSettings.smsNotifications").value(true))
                .andExpect(jsonPath("$.notificationSettings.reminderBeforeDue").value(12));
    }

    @Test
    void resetConfig_restoresDefaults() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        put("/api/v1/" + ctx.businessId() + "/config", ctx.accessToken(), fullConfigBody())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("USD"));

        assertSuccess(post("/api/v1/" + ctx.businessId() + "/config/reset", ctx.accessToken(), null),
                "Configuration reset to defaults");
        get("/api/v1/" + ctx.businessId() + "/config", ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("NGN"))
                .andExpect(jsonPath("$.timezone").value("Africa/Lagos"))
                .andExpect(jsonPath("$.notificationSettings.emailNotifications").value(true));
    }

    @Test
    void config_crossTenantDenied() throws Exception {
        AuthContext a = registerBusinessAndOwner();
        AuthContext b = registerBusinessAndOwner();
        get("/api/v1/" + b.businessId() + "/config", a.accessToken())
                .andExpect(status().isForbidden());
    }

    @Test
    void config_requiresAuth() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        get("/api/v1/" + ctx.businessId() + "/config", null).andExpect(status().isUnauthorized());
    }
}
