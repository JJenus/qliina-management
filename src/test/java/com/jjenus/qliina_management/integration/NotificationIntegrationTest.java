package com.jjenus.qliina_management.integration;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Notification suite: NotificationController — /api/v1/{businessId}/notifications.
 * (notifications, mark-read, send, templates, test, logs, stats, devices, preferences.)
 */
class NotificationIntegrationTest extends BaseIntegrationTest {

    private String base(UUID businessId) {
        return "/api/v1/" + businessId + "/notifications";
    }

    private Map<String, Object> sendBody(UUID userId) {
        return Map.of(
                "recipients", List.of(userId.toString()),
                "type", "ALERT",
                "channel", "IN_APP",
                "title", "Low stock",
                "body", "Item X is below threshold",
                "priority", "HIGH");
    }

    private String sendAlert(AuthContext ctx) throws Exception {
        MvcResult res = post(base(ctx.businessId()) + "/send", ctx.accessToken(),
                sendBody(ctx.userId()))
                .andExpect(status().isOk())
                .andReturn();
        return res.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private Map<String, Object> templateBody() {
        return Map.of(
                "name", "Order Ready",
                "description", "Tells customer order is ready",
                "type", "ORDER_STATUS",
                "channel", "EMAIL",
                "subject", "Your order is ready",
                "titleTemplate", "Ready: {{orderNumber}}",
                "bodyTemplate", "Hi {{userName}}, your order is ready for pickup.",
                "variables", List.of("orderNumber", "userName"));
    }

    private UUID createTemplate(UUID businessId, String token) throws Exception {
        String json = post(base(businessId) + "/templates", token, templateBody())
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return readUuid(json, "$.id");
    }

    // ---------------------------------------------------------------------
    // Notifications list / unread / mark-read
    // ---------------------------------------------------------------------

    @Test
    void listNotifications_emptyInitially() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        get(base(ctx.businessId()), ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void sendAndListAndUnread() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        sendAlert(ctx);

        get(base(ctx.businessId()), ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].type").value("ALERT"))
                .andExpect(jsonPath("$.content[0].channel").value("IN_APP"))
                .andExpect(jsonPath("$.content[0].status").value("DELIVERED"))
                .andExpect(jsonPath("$.content[0].priority").value("HIGH"))
                .andExpect(jsonPath("$.content[0].title").value("Low stock"))
                .andExpect(jsonPath("$.content[0].isRead").value(false));

        get(base(ctx.businessId()) + "/unread-count", ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));
    }

    @Test
    void markRead_allClearsUnread() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        sendAlert(ctx);

        post(base(ctx.businessId()) + "/mark-read", ctx.accessToken(), Map.of("markAll", true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Notifications marked as read"));

        get(base(ctx.businessId()) + "/unread-count", ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0));
    }

    @Test
    void markRead_byIds() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        String first = sendAlert(ctx);
        sendAlert(ctx);
        String firstId = readString(first, "$.id");

        post(base(ctx.businessId()) + "/mark-read", ctx.accessToken(),
                Map.of("notificationIds", List.of(firstId)))
                .andExpect(status().isOk());

        get(base(ctx.businessId()) + "/unread-count", ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));
    }

    @Test
    void markRead_emptyBodySucceeds() throws Exception {
        // Documented divergence: MarkReadRequest has no validation constraints;
        // an empty body is a successful no-op.
        AuthContext ctx = registerBusinessAndOwner();
        post(base(ctx.businessId()) + "/mark-read", ctx.accessToken(), Map.of())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void listNotifications_typeFilter() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        sendAlert(ctx);

        get(base(ctx.businessId()) + "?type=ALERT", ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        get(base(ctx.businessId()) + "?type=PAYMENT", ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void notifications_requiresAuth() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        get(base(ctx.businessId()), null).andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------------
    // Send
    // ---------------------------------------------------------------------

    @Test
    void sendNotification_validation() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertValidation(post(base(ctx.businessId()) + "/send", ctx.accessToken(), Map.of()),
                Map.of(
                        "type", "Notification type is required",
                        "channel", "Channel is required"));
    }

    @Test
    void sendNotification_invalidType_400() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertProblemDetail(post(base(ctx.businessId()) + "/send", ctx.accessToken(),
                Map.of("type", "FANCY", "channel", "IN_APP", "title", "t", "body", "b")),
                400, "INVALID_REQUEST");
    }

    @Test
    void sendNotification_templateNotFound_400() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertProblemDetail(post(base(ctx.businessId()) + "/send", ctx.accessToken(),
                Map.of("recipients", List.of(ctx.userId().toString()),
                        "type", "ALERT", "channel", "IN_APP",
                        "templateId", UUID.randomUUID().toString())),
                400, "TEMPLATE_NOT_FOUND");
    }

    @Test
    void sendNotification_withTemplate() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        UUID templateId = createTemplate(ctx.businessId(), ctx.accessToken());

        post(base(ctx.businessId()) + "/send", ctx.accessToken(),
                Map.of("recipients", List.of(ctx.userId().toString()),
                        "type", "ORDER_STATUS", "channel", "IN_APP",
                        "templateId", templateId.toString(),
                        "templateData", Map.of("orderNumber", "ORD-123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("ORDER_STATUS"))
                .andExpect(jsonPath("$.channel").value("IN_APP"))
                .andExpect(jsonPath("$.status").value("DELIVERED"));
    }

    @Test
    void sendNotification_noRecipientsSendsToAll() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        // No recipients -> falls back to all business users (owner only here).
        post(base(ctx.businessId()) + "/send", ctx.accessToken(),
                Map.of("type", "SYSTEM", "channel", "IN_APP", "title", "t", "body", "b"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(ctx.userId().toString()));

        get(base(ctx.businessId()), ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    // ---------------------------------------------------------------------
    // Templates
    // ---------------------------------------------------------------------

    @Test
    void templates_crud() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        UUID templateId = createTemplate(ctx.businessId(), ctx.accessToken());

        get(base(ctx.businessId()) + "/templates", ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(templateId.toString()))
                .andExpect(jsonPath("$[0].name").value("Order Ready"))
                .andExpect(jsonPath("$[0].type").value("ORDER_STATUS"))
                .andExpect(jsonPath("$[0].channel").value("EMAIL"))
                .andExpect(jsonPath("$[0].isActive").value(true));

        put(base(ctx.businessId()) + "/templates/" + templateId, ctx.accessToken(),
                Map.of("name", "Order Ready v2", "isActive", false))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Order Ready v2"))
                .andExpect(jsonPath("$.isActive").value(false));

        delete(base(ctx.businessId()) + "/templates/" + templateId, ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Template deleted successfully"));

        get(base(ctx.businessId()) + "/templates", ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void templates_validation() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertValidation(post(base(ctx.businessId()) + "/templates", ctx.accessToken(), Map.of()),
                Map.of(
                        "name", "Template name is required",
                        "type", "Notification type is required",
                        "channel", "Channel is required",
                        "bodyTemplate", "Body template is required"));
    }

    @Test
    void templates_updateNotFound_400() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertProblemDetail(put(base(ctx.businessId()) + "/templates/" + UUID.randomUUID(),
                ctx.accessToken(), Map.of("name", "x")),
                400, "TEMPLATE_NOT_FOUND");
    }

    @Test
    void templates_crossTenantDelete_400() throws Exception {
        AuthContext ownerA = registerBusinessAndOwner();
        AuthContext ownerB = registerBusinessAndOwner();
        UUID templateId = createTemplate(ownerA.businessId(), ownerA.accessToken());

        // Business B cannot mutate business A's template (IDOR guarded).
        assertProblemDetail(delete(base(ownerB.businessId()) + "/templates/" + templateId,
                ownerB.accessToken()),
                400, "TEMPLATE_NOT_FOUND");
    }

    // ---------------------------------------------------------------------
    // Test / logs / stats
    // ---------------------------------------------------------------------

    @Test
    void testNotification_push_success() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        post(base(ctx.businessId()) + "/test", ctx.accessToken(),
                Map.of("recipient", "recipient@example.com",
                        "channel", "PUSH",
                        "customTitle", "Test",
                        "customBody", "Hello"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Test notification sent"));
    }

    @Test
    void testNotification_validation() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertValidation(post(base(ctx.businessId()) + "/test", ctx.accessToken(), Map.of()),
                Map.of(
                        "recipient", "Recipient is required",
                        "channel", "Channel is required"));
    }

    @Test
    void testNotification_emailNotConfigured_400() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertProblemDetail(post(base(ctx.businessId()) + "/test", ctx.accessToken(),
                Map.of("recipient", "recipient@example.com",
                        "channel", "EMAIL",
                        "customTitle", "Test",
                        "customBody", "Hello")),
                400, "EMAIL_NOT_CONFIGURED");
    }

    @Test
    void logs_emptyInitially() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        get(base(ctx.businessId()) + "/logs", ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void stats_success() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        String from = java.time.LocalDateTime.now().minusDays(1)
                .format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        String to = java.time.LocalDateTime.now().plusDays(1)
                .format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        get(base(ctx.businessId()) + "/stats?startDate=" + from + "&endDate=" + to, ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSent").exists())
                .andExpect(jsonPath("$.totalDelivered").exists())
                .andExpect(jsonPath("$.totalFailed").exists())
                .andExpect(jsonPath("$.successRate").exists())
                .andExpect(jsonPath("$.byChannel").exists())
                .andExpect(jsonPath("$.byStatus").exists());
    }

    // ---------------------------------------------------------------------
    // Devices
    // ---------------------------------------------------------------------

    @Test
    void devices_registerListUnregister() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();

        post(base(ctx.businessId()) + "/devices/register", ctx.accessToken(),
                Map.of("deviceId", "dev-123", "deviceType", "ANDROID",
                        "pushToken", "tok-abc", "appVersion", "1.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deviceId").value("dev-123"))
                .andExpect(jsonPath("$.deviceType").value("ANDROID"))
                .andExpect(jsonPath("$.isActive").value(true));

        get(base(ctx.businessId()) + "/devices", ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].deviceId").value("dev-123"));

        delete(base(ctx.businessId()) + "/devices/dev-123", ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Device unregistered"));

        get(base(ctx.businessId()) + "/devices", ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void devices_register_validation() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertValidation(post(base(ctx.businessId()) + "/devices/register", ctx.accessToken(), Map.of()),
                Map.of(
                        "deviceId", "Device ID is required",
                        "deviceType", "Device type is required"));
    }

    @Test
    void devices_unregisterUnknown_success() throws Exception {
        // Documented divergence: unregistering an unknown device returns 200 success (no-op).
        AuthContext ctx = registerBusinessAndOwner();
        delete(base(ctx.businessId()) + "/devices/nope", ctx.accessToken())
                .andExpect(status().isOk());
    }

    // ---------------------------------------------------------------------
    // Preferences
    // ---------------------------------------------------------------------

    @Test
    void preferences_getAndUpdate() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        get(base(ctx.businessId()) + "/preferences", ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        put(base(ctx.businessId()) + "/preferences", ctx.accessToken(),
                Map.of("channel", "EMAIL", "notificationType", "ORDER_STATUS", "enabled", true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.channel").value("EMAIL"))
                .andExpect(jsonPath("$.notificationType").value("ORDER_STATUS"))
                .andExpect(jsonPath("$.enabled").value(true));

        // Defaults are seeded for every channel x type combination; search the array.
        MvcResult res = get(base(ctx.businessId()) + "/preferences", ctx.accessToken())
                .andExpect(status().isOk()).andReturn();
        String json = res.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertPreferenceEnabled(json, "EMAIL", "ORDER_STATUS", true);
        assertPreferenceEnabled(json, "WHATSAPP", "ORDER_STATUS", false);
    }

    private void assertPreferenceEnabled(String json, String channel, String type, boolean enabled)
            throws Exception {
        List<Map<String, Object>> prefs = com.jayway.jsonpath.JsonPath.read(json, "$");
        Map<String, Object> match = prefs.stream()
                .filter(p -> channel.equals(p.get("channel")))
                .filter(p -> type.equals(p.get("notificationType")))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "No preference for " + channel + "/" + type + " in " + prefs));
        if (!Boolean.valueOf(enabled).equals(match.get("enabled"))) {
            throw new AssertionError("Expected " + channel + "/" + type + " enabled=" + enabled
                    + " but was " + match.get("enabled"));
        }
    }

    @Test
    void preferences_update_validation() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertValidation(put(base(ctx.businessId()) + "/preferences", ctx.accessToken(), Map.of()),
                Map.of(
                        "channel", "must not be null",
                        "notificationType", "must not be null"));
    }

    @Test
    void preferences_invalidChannel_400() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertProblemDetail(put(base(ctx.businessId()) + "/preferences", ctx.accessToken(),
                Map.of("channel", "FAX", "notificationType", "ORDER_STATUS", "enabled", true)),
                400, "INVALID_REQUEST");
    }

    // ---------------------------------------------------------------------
    // Permissions
    // ---------------------------------------------------------------------

    @Test
    void templates_requiresManagePermission() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        // FRONT_DESK has notification.view/update but not notification.manage.
        String frontDesk = createEmployee(ctx, "FRONT_DESK");
        assertProblemDetail(post(base(ctx.businessId()) + "/templates", frontDesk, templateBody()),
                403, "ACCESS_DENIED");
    }

    private String createEmployee(AuthContext ctx, String roleName) throws Exception {
        MvcResult rolesRes = get("/api/v1/" + ctx.businessId() + "/users/available-roles", ctx.accessToken())
                .andExpect(status().isOk()).andReturn();
        String rolesJson = rolesRes.getResponse().getContentAsString(StandardCharsets.UTF_8);
        List<String> names = com.jayway.jsonpath.JsonPath.read(rolesJson, "$[*].name");
        List<String> ids = com.jayway.jsonpath.JsonPath.read(rolesJson, "$[*].id");
        int idx = names.indexOf(roleName);
        if (idx < 0) throw new IllegalStateException("Role not found: " + roleName);

        String unique = random();
        String username = roleName.toLowerCase() + "_" + unique;
        Map<String, Object> body = new HashMap<>();
        body.put("username", username);
        body.put("email", username + "@test.com");
        body.put("phone", "+1" + (555_600_0000L + counter.incrementAndGet()));
        body.put("firstName", roleName);
        body.put("lastName", "Staff");
        body.put("password", DEFAULT_PASSWORD);
        body.put("confirmPassword", DEFAULT_PASSWORD);
        body.put("roles", List.of(Map.of("roleId", ids.get(idx), "shopId", ctx.shopId().toString())));

        post("/api/v1/" + ctx.businessId() + "/users", ctx.accessToken(), body)
                .andExpect(status().isOk());

        return loginToken(username, DEFAULT_PASSWORD);
    }
}
