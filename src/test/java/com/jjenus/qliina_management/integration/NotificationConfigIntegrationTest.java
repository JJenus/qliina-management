package com.jjenus.qliina_management.integration;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Notification config suite: NotificationConfigController —
 * /api/v1/{businessId}/notifications/config (email, sms, push).
 */
class NotificationConfigIntegrationTest extends BaseIntegrationTest {

    private String base(UUID businessId) {
        return "/api/v1/" + businessId + "/notifications/config";
    }

    // ---------------------------------------------------------------------
    // Email
    // ---------------------------------------------------------------------

    @Test
    void emailConfig_notConfigured_400() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertProblemDetail(get(base(ctx.businessId()) + "/email", ctx.accessToken()),
                400, "EMAIL_CONFIG_NOT_FOUND");
    }

    @Test
    void emailConfig_configureAndGet() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        post(base(ctx.businessId()) + "/email", ctx.accessToken(), Map.of(
                "host", "smtp.example.com",
                "port", 587,
                "username", "noreply@example.com",
                "password", "secret",
                "fromAddress", "noreply@example.com",
                "fromName", "Qliina",
                "useTls", true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessId").value(ctx.businessId().toString()))
                .andExpect(jsonPath("$.host").value("smtp.example.com"))
                .andExpect(jsonPath("$.port").value(587))
                .andExpect(jsonPath("$.fromAddress").value("noreply@example.com"))
                .andExpect(jsonPath("$.useTls").value(true));

        get(base(ctx.businessId()) + "/email", ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.host").value("smtp.example.com"))
                .andExpect(jsonPath("$.isConfigured").value(true));
    }

    @Test
    void emailConfig_test_requiresExistingConfig() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertProblemDetail(post(base(ctx.businessId()) + "/email/test?testEmail=a@b.com",
                ctx.accessToken(), Map.of()),
                400, "EMAIL_NOT_CONFIGURED");
    }

    // ---------------------------------------------------------------------
    // SMS
    // ---------------------------------------------------------------------

    @Test
    void smsConfig_notConfigured_400() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertProblemDetail(get(base(ctx.businessId()) + "/sms", ctx.accessToken()),
                400, "SMS_CONFIG_NOT_FOUND");
    }

    @Test
    void smsConfig_configureAndGet() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        post(base(ctx.businessId()) + "/sms", ctx.accessToken(), Map.of(
                "provider", "TWILIO",
                "fromNumber", "+15551234567",
                "accountSid", "sid",
                "authToken", "token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("TWILIO"))
                .andExpect(jsonPath("$.fromNumber").value("+15551234567"))
                .andExpect(jsonPath("$.isConfigured").value(true));

        get(base(ctx.businessId()) + "/sms", ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("TWILIO"));
    }

    @Test
    void smsConfig_test_requiresExistingConfig() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertProblemDetail(post(base(ctx.businessId()) + "/sms/test?testPhoneNumber=%2B15551234567",
                ctx.accessToken(), Map.of()),
                400, "SMS_NOT_CONFIGURED");
    }

    // ---------------------------------------------------------------------
    // Push
    // ---------------------------------------------------------------------

    @Test
    void pushConfig_notConfigured_400() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertProblemDetail(get(base(ctx.businessId()) + "/push", ctx.accessToken()),
                400, "PUSH_CONFIG_NOT_FOUND");
    }

    @Test
    void pushConfig_configureAndGet() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        post(base(ctx.businessId()) + "/push", ctx.accessToken(), Map.of(
                "firebaseProjectId", "proj",
                "serviceAccountJson", "{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isConfigured").value(true));

        get(base(ctx.businessId()) + "/push", ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isConfigured").value(true));
    }

    // ---------------------------------------------------------------------
    // Permissions / auth
    // ---------------------------------------------------------------------

    @Test
    void config_requiresAuth() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        get(base(ctx.businessId()) + "/email", null).andExpect(status().isUnauthorized());
    }

    @Test
    void config_crossTenant_403() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        AuthContext other = registerBusinessAndOwner();
        assertProblemDetail(get(base(ctx.businessId()) + "/email", other.accessToken()),
                403, "ACCESS_DENIED");
    }
}
