package com.jjenus.qliina_management.integration;

import com.jjenus.qliina_management.identity.model.PasswordResetToken;
import com.jjenus.qliina_management.identity.model.User;
import com.jjenus.qliina_management.identity.repository.PasswordResetTokenRepository;
import com.jjenus.qliina_management.identity.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private UserRepository userRepository;

    private String resetTokenHash(String raw) throws Exception {
        return java.util.HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
    }

    // ---------------------------------------------------------------------
    // Register business
    // ---------------------------------------------------------------------

    @Test
    void registerBusiness_success_returns201WithTokensAndResources() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();

        // Tokens and metadata
        assertThat(ctx.accessToken()).isNotBlank();
        assertThat(ctx.businessId()).isNotNull();
        assertThat(ctx.shopId()).isNotNull();
        assertThat(ctx.userId()).isNotNull();

        // Re-login works (tokens are real)
        String token = loginToken(ctx.username(), ctx.password());
        assertThat(token).isNotBlank();
    }

    @Test
    void registerBusiness_success_responseShape() throws Exception {
        String unique = random();
        Map<String, Object> body = validRegistrationBody(unique);
        MvcResult res = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/auth/register-business")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(86400))
                .andExpect(jsonPath("$.businessId").isNotEmpty())
                .andExpect(jsonPath("$.businessSlug", org.hamcrest.Matchers.startsWith("biz-")))
                .andExpect(jsonPath("$.shopId").isNotEmpty())
                .andExpect(jsonPath("$.user.username").value("owner_" + unique))
                .andExpect(jsonPath("$.user.email").value("owner_" + unique + "@test.com"))
                .andExpect(jsonPath("$.user.businessId").isNotEmpty())
                .andExpect(jsonPath("$.user.roles[0]").value("BUSINESS_ADMIN"))
                .andReturn();
        assertThat(extractString(res, "$.user.permissions")).isNotNull();
    }

    @Test
    void registerBusiness_passwordMismatch_returns400() throws Exception {
        Map<String, Object> body = validRegistrationBody(random());
        body.put("confirmPassword", "different-password");
        assertProblemDetail(post("/api/v1/auth/register-business", null, body),
                400, "PASSWORD_MISMATCH");
    }

    @Test
    void registerBusiness_duplicateUsername_returns400() throws Exception {
        String unique = random();
        AuthContext ctx = registerBusinessAndOwner("dupuser_" + unique);
        Map<String, Object> body = validRegistrationBody(random());
        body.put("username", ctx.username());
        body.put("email", "other_" + unique + "@test.com");
        body.put("phone", "+1" + (555_100_0000L + counter.incrementAndGet()));
        MvcResult res = post("/api/v1/auth/register-business", null, body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field").value("username"))
                .andExpect(jsonPath("$.errorCode").value("USERNAME_EXISTS"))
                .andReturn();
        assertThat(res.getResponse().getContentAsString()).isNotNull();
    }

    @Test
    void registerBusiness_duplicateEmail_returns400() throws Exception {
        String unique = random();
        AuthContext ctx = registerBusinessAndOwner("dupemail_" + unique);
        Map<String, Object> body = validRegistrationBody(random());
        body.put("email", ctx.email());
        body.put("username", "newuser_" + unique);
        body.put("phone", "+1" + (555_200_0000L + counter.incrementAndGet()));
        post("/api/v1/auth/register-business", null, body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field").value("email"))
                .andExpect(jsonPath("$.errorCode").value("EMAIL_EXISTS"));
    }

    @Test
    void registerBusiness_duplicatePhone_returns400() throws Exception {
        String unique = random();
        AuthContext ctx = registerBusinessAndOwner("dupphone_" + unique);
        Map<String, Object> body = validRegistrationBody(random());
        body.put("phone", ctx.phone());
        body.put("username", "newuser2_" + unique);
        body.put("email", "new2_" + unique + "@test.com");
        post("/api/v1/auth/register-business", null, body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field").value("phone"))
                .andExpect(jsonPath("$.errorCode").value("PHONE_EXISTS"));
    }

    @Test
    void registerBusiness_validation_blankRequiredFields() throws Exception {
        Map<String, Object> body = validRegistrationBody(random());
        body.put("businessName", "");
        body.put("shopName", "");
        body.put("firstName", "");
        body.put("lastName", "");
        body.put("username", "");
        body.put("email", "");
        body.put("phone", "");
        body.put("password", "");
        body.put("confirmPassword", "");
        assertValidation(post("/api/v1/auth/register-business", null, body), Map.of(
                "businessName", "Business name is required",
                "shopName", "Shop name is required",
                "firstName", "First name is required",
                "lastName", "Last name is required",
                "username", "Username is required",
                "email", "Email is required",
                "phone", "Phone is required",
                "password", "Password is required",
                "confirmPassword", "Please confirm your password"
        ));
    }

    @Test
    void registerBusiness_validation_usernameTooShort() throws Exception {
        Map<String, Object> body = validRegistrationBody(random());
        body.put("username", "ab");
        assertValidation(post("/api/v1/auth/register-business", null, body),
                Map.of("username", "Username must be 3-50 characters"));
    }

    @Test
    void registerBusiness_validation_shortPassword() throws Exception {
        Map<String, Object> body = validRegistrationBody(random());
        body.put("password", "short");
        body.put("confirmPassword", "short");
        assertValidation(post("/api/v1/auth/register-business", null, body),
                Map.of("password", "Password must be at least 8 characters"));
    }

    @Test
    void registerBusiness_validation_badEmailFormat() throws Exception {
        Map<String, Object> body = validRegistrationBody(random());
        body.put("email", "not-an-email");
        assertValidation(post("/api/v1/auth/register-business", null, body),
                Map.of("email", "Invalid email format"));
    }

    // ---------------------------------------------------------------------
    // Login
    // ---------------------------------------------------------------------

    @Test
    void login_success_returns200WithTokens() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        MvcResult res = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/auth/login")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of(
                                "username", ctx.username(),
                                "password", ctx.password()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(86400))
                .andExpect(jsonPath("$.requires2FA").value(false))
                .andExpect(jsonPath("$.user.username").value(ctx.username()))
                .andExpect(jsonPath("$.user.businessId").value(ctx.businessId().toString()))
                .andReturn();
        assertThat(res.getResponse().getContentAsString()).contains("permissions");
    }

    @Test
    void login_wrongPassword_returns401InvalidCredentials() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertProblemDetail(post("/api/v1/auth/login", null, Map.of(
                "username", ctx.username(),
                "password", "WrongPass1!")), 401, "INVALID_CREDENTIALS");
    }

    @Test
    void login_unknownUser_returns401InvalidCredentials() throws Exception {
        assertProblemDetail(post("/api/v1/auth/login", null, Map.of(
                "username", "nobody_" + random(),
                "password", "Whatever1!")), 401, "INVALID_CREDENTIALS");
    }

    @Test
    void login_locksAccountAfterFiveFailures() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        for (int i = 0; i < 5; i++) {
            post("/api/v1/auth/login", null, Map.of(
                    "username", ctx.username(),
                    "password", "WrongPass1!"))
                    .andExpect(status().isUnauthorized());
        }
        // Even the correct password is now rejected because the account is locked.
        assertProblemDetail(post("/api/v1/auth/login", null, Map.of(
                "username", ctx.username(),
                "password", ctx.password())), 400, "ACCOUNT_LOCKED");
    }

    @Test
    void login_validation_blankCredentials() throws Exception {
        assertValidation(post("/api/v1/auth/login", null, Map.of(
                "username", "",
                "password", "")), Map.of(
                "username", "Username is required",
                "password", "Password is required"));
    }

    @Test
    void login_blockedWhenBusinessSuspended() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        String admin = adminToken();
        patch("/api/v1/admin/businesses/" + ctx.businessId() + "/status", admin, Map.of("status", "SUSPENDED"))
                .andExpect(status().isOk());
        assertProblemDetail(post("/api/v1/auth/login", null, Map.of(
                "username", ctx.username(),
                "password", ctx.password())), 400, "BUSINESS_NOT_ACTIVE");
        // Restore for other tests sharing this context
        patch("/api/v1/admin/businesses/" + ctx.businessId() + "/status", admin, Map.of("status", "ACTIVE"))
                .andExpect(status().isOk());
    }

    // ---------------------------------------------------------------------
    // Refresh / logout
    // ---------------------------------------------------------------------

    @Test
    void refreshToken_rotatesPair() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        String refreshToken = refreshTokenFor(ctx);
        MvcResult res = post("/api/v1/auth/refresh", null, Map.of("refreshToken", refreshToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn();
        assertThat(extractString(res, "$.refreshToken")).isNotEqualTo(refreshToken);
        // Old token is now revoked (single-session policy deletes it on rotation)
        assertProblemDetail(post("/api/v1/auth/refresh", null, Map.of("refreshToken", refreshToken)),
                400, "REFRESH_TOKEN_NOT_FOUND");
    }

    @Test
    void refreshToken_unknownToken_returns400() throws Exception {
        assertProblemDetail(post("/api/v1/auth/refresh", null, Map.of("refreshToken", "definitely-not-a-token")),
                400, "REFRESH_TOKEN_NOT_FOUND");
    }

    @Test
    void refreshToken_blank_returnsValidation() throws Exception {
        assertValidation(post("/api/v1/auth/refresh", null, Map.of("refreshToken", "")),
                Map.of("refreshToken", "Refresh token is required"));
    }

    @Test
    void logout_revokesRefreshToken() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        String refreshToken = refreshTokenFor(ctx);
        assertSuccess(post("/api/v1/auth/logout", null, Map.of("refreshToken", refreshToken)),
                "Logged out successfully");
        assertProblemDetail(post("/api/v1/auth/refresh", null, Map.of("refreshToken", refreshToken)),
                400, "REFRESH_TOKEN_INVALID");
    }

    // ---------------------------------------------------------------------
    // Password flows
    // ---------------------------------------------------------------------

    @Test
    void forgotPassword_alwaysReturnsSameMessage() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertSuccess(post("/api/v1/auth/forgot-password", null, Map.of("username", ctx.username())),
                "If the account exists, a reset link will be sent");
        assertSuccess(post("/api/v1/auth/forgot-password", null, Map.of("username", "ghost_" + random())),
                "If the account exists, a reset link will be sent");
    }

    @Test
    void resetPassword_success_usesHashedToken() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        String rawToken = "reset-token-" + random();
        persistResetToken(ctx.userId(), rawToken, LocalDateTime.now().plusHours(1), null);

        assertSuccess(post("/api/v1/auth/reset-password", null, Map.of(
                "token", rawToken,
                "newPassword", "NewPassw0rd!",
                "confirmPassword", "NewPassw0rd!")), "Password reset successfully");

        // Old password no longer works, new one does
        post("/api/v1/auth/login", null, Map.of("username", ctx.username(), "password", ctx.password()))
                .andExpect(status().isUnauthorized());
        loginToken(ctx.username(), "NewPassw0rd!");
    }

    @Test
    void resetPassword_mismatch_returns400() throws Exception {
        assertProblemDetail(post("/api/v1/auth/reset-password", null, Map.of(
                "token", "anything",
                "newPassword", "NewPassw0rd!",
                "confirmPassword", "Different1!")), 400, "PASSWORD_MISMATCH");
    }

    @Test
    void resetPassword_invalidToken_returns400() throws Exception {
        assertProblemDetail(post("/api/v1/auth/reset-password", null, Map.of(
                "token", "no-such-token",
                "newPassword", "NewPassw0rd!",
                "confirmPassword", "NewPassw0rd!")), 400, "INVALID_RESET_TOKEN");
    }

    @Test
    void resetPassword_expiredToken_returns400() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        String rawToken = "expired-token-" + random();
        persistResetToken(ctx.userId(), rawToken, LocalDateTime.now().minusMinutes(1), null);
        assertProblemDetail(post("/api/v1/auth/reset-password", null, Map.of(
                "token", rawToken,
                "newPassword", "NewPassw0rd!",
                "confirmPassword", "NewPassw0rd!")), 400, "RESET_TOKEN_EXPIRED");
    }

    @Test
    void resetPassword_usedToken_returns400() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        String rawToken = "used-token-" + random();
        persistResetToken(ctx.userId(), rawToken, LocalDateTime.now().plusHours(1), LocalDateTime.now());
        assertProblemDetail(post("/api/v1/auth/reset-password", null, Map.of(
                "token", rawToken,
                "newPassword", "NewPassw0rd!",
                "confirmPassword", "NewPassw0rd!")), 400, "RESET_TOKEN_USED");
    }

    @Test
    void changePassword_success() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertSuccess(post("/api/v1/auth/change-password", ctx.accessToken(), Map.of(
                "currentPassword", ctx.password(),
                "newPassword", "Changed1!",
                "confirmPassword", "Changed1!")), "Password changed successfully");
        loginToken(ctx.username(), "Changed1!");
    }

    @Test
    void changePassword_wrongCurrentPassword_returns400() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertProblemDetail(post("/api/v1/auth/change-password", ctx.accessToken(), Map.of(
                "currentPassword", "WrongPass1!",
                "newPassword", "Changed1!",
                "confirmPassword", "Changed1!")), 400, "INVALID_PASSWORD");
    }

    @Test
    void changePassword_mismatch_returns400() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertProblemDetail(post("/api/v1/auth/change-password", ctx.accessToken(), Map.of(
                "currentPassword", ctx.password(),
                "newPassword", "Changed1!",
                "confirmPassword", "Different1!")), 400, "PASSWORD_MISMATCH");
    }

    @Test
    void changePassword_withoutToken_returns500() throws Exception {
        // Documented divergence: the controller throws an unhandled
        // UsernameNotFoundException when no principal is present.
        post("/api/v1/auth/change-password", null, Map.of(
                "currentPassword", "x",
                "newPassword", "Changed1!",
                "confirmPassword", "Changed1!"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"));
    }

    // ---------------------------------------------------------------------
    // 2FA
    // ---------------------------------------------------------------------

    @Test
    void setup2fa_success_returnsSecretAndQrCode() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        MvcResult res = post("/api/v1/auth/setup-2fa", null, Map.of("userId", ctx.userId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.secret").isNotEmpty())
                .andExpect(jsonPath("$.qrCodeUrl").value(org.hamcrest.Matchers.startsWith("otpauth://totp/Qliina:")))
                .andReturn();
        List<?> codes = com.jayway.jsonpath.JsonPath.read(
                res.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.backupCodes");
        assertThat(codes).hasSize(8);
    }

    @Test
    void setup2fa_userNotFound_returns400() throws Exception {
        assertProblemDetail(post("/api/v1/auth/setup-2fa", null, Map.of("userId", UUID.randomUUID().toString())),
                400, "USER_NOT_FOUND");
    }

    @Test
    void verify2fa_notImplemented_returns400() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertProblemDetail(post("/api/v1/auth/verify-2fa", null, Map.of(
                "userId", ctx.userId().toString(),
                "code", "123456")), 400, "TOTP_NOT_IMPLEMENTED");
    }

    @Test
    void verify2fa_userNotFound_returns400() throws Exception {
        assertProblemDetail(post("/api/v1/auth/verify-2fa", null, Map.of(
                "userId", UUID.randomUUID().toString(),
                "code", "123456")), 400, "USER_NOT_FOUND");
    }

    @Test
    void disable2fa_success() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        post("/api/v1/auth/setup-2fa", null, Map.of("userId", ctx.userId().toString()))
                .andExpect(status().isOk());
        assertSuccess(post("/api/v1/auth/disable-2fa", null, Map.of("userId", ctx.userId().toString())),
                "2FA disabled successfully");
    }

    @Test
    void disable2fa_userNotFound_returns400() throws Exception {
        assertProblemDetail(post("/api/v1/auth/disable-2fa", null, Map.of("userId", UUID.randomUUID().toString())),
                400, "USER_NOT_FOUND");
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private Map<String, Object> validRegistrationBody(String unique) {
        Map<String, Object> body = new HashMap<>();
        body.put("businessName", "Test Business " + unique);
        body.put("slug", "biz_" + unique);
        body.put("businessEmail", "biz_" + unique + "@test.com");
        body.put("shopName", "Main Shop");
        body.put("shopCode", "SH" + unique.substring(0, 4).toUpperCase());
        body.put("firstName", "Owner");
        body.put("lastName", "One");
        body.put("username", "owner_" + unique);
        body.put("email", "owner_" + unique + "@test.com");
        body.put("phone", "+1" + (555_000_0000L + counter.incrementAndGet()));
        body.put("password", DEFAULT_PASSWORD);
        body.put("confirmPassword", DEFAULT_PASSWORD);
        return body;
    }

    private String refreshTokenFor(AuthContext ctx) throws Exception {
        MvcResult res = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/auth/login")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of(
                                "username", ctx.username(),
                                "password", ctx.password()))))
                .andExpect(status().isOk())
                .andReturn();
        return extractString(res, "$.refreshToken");
    }

    private void persistResetToken(UUID userId, String rawToken, LocalDateTime expiresAt, LocalDateTime usedAt)
            throws Exception {
        User user = userRepository.findById(userId).orElseThrow();
        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        token.setTokenHash(resetTokenHash(rawToken));
        token.setExpiresAt(expiresAt);
        token.setUsedAt(usedAt);
        passwordResetTokenRepository.save(token);
    }
}
