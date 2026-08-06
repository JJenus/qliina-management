package com.jjenus.qliina_management.integration;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Payment Methods suite: PaymentMethodController — /api/v1/{businessId}/payment-methods.
 */
class PaymentMethodIntegrationTest extends BaseIntegrationTest {

    private String base(UUID businessId) {
        return "/api/v1/" + businessId + "/payment-methods";
    }

    private ResultActions createMethod(AuthContext ctx, String type, String name) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("type", type);
        return post(base(ctx.businessId()), ctx.accessToken(), body);
    }

    @Test
    void listMethods_returnsDefaults() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        get(base(ctx.businessId()), ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(3)))
                .andExpect(jsonPath("$[?(@.type=='CASH')].isActive").value(true));
    }

    @Test
    void listMethods_requiresAuth() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        get(base(ctx.businessId()), null).andExpect(status().isUnauthorized());
    }

    @Test
    void listMethods_crossTenantDenied() throws Exception {
        AuthContext a = registerBusinessAndOwner();
        AuthContext b = registerBusinessAndOwner();
        get(base(b.businessId()), a.accessToken())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    void getMethod_success() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        String listJson = get(base(ctx.businessId()), ctx.accessToken())
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        UUID methodId = readUuid(listJson, "$[0].id");

        get(base(ctx.businessId()) + "/" + methodId, ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(methodId.toString()))
                .andExpect(jsonPath("$.type").exists())
                .andExpect(jsonPath("$.isActive").value(true));
    }

    @Test
    void getMethod_notFound() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertProblemDetail(get(base(ctx.businessId()) + "/" + UUID.randomUUID(), ctx.accessToken()),
                400, "METHOD_NOT_FOUND");
    }

    @Test
    void createMethod_success() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        createMethod(ctx, "QRCODE", "QR Code")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("QRCODE"))
                .andExpect(jsonPath("$.name").value("QR Code"))
                .andExpect(jsonPath("$.isActive").value(true));

        // Type is stored uppercased.
        createMethod(ctx, "coupon", "Coupon")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("COUPON"));
    }

    @Test
    void createMethod_duplicateType() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        createMethod(ctx, "QRCODE", "QR Code").andExpect(status().isOk());

        assertProblemDetail(createMethod(ctx, "QRCODE", "QR Code v2"),
                400, "Business Rule Violation", "METHOD_EXISTS");
    }

    @Test
    void createMethod_validation() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertValidation(post(base(ctx.businessId()), ctx.accessToken(), Map.of()),
                Map.of(
                        "name", "Payment method name is required",
                        "type", "Payment method type is required"));
    }

    @Test
    void updateMethod_success() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        String listJson = get(base(ctx.businessId()), ctx.accessToken())
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        UUID methodId = readUuid(listJson, "$[0].id");

        put(base(ctx.businessId()) + "/" + methodId, ctx.accessToken(),
                Map.of("name", "Renamed Method"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(methodId.toString()))
                .andExpect(jsonPath("$.name").value("Renamed Method"));
    }

    @Test
    void updateMethod_notFound() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertProblemDetail(put(base(ctx.businessId()) + "/" + UUID.randomUUID(),
                ctx.accessToken(), Map.of("name", "x")), 400, "METHOD_NOT_FOUND");
    }

    @Test
    void toggleMethod_disableAndEnable() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        String listJson = get(base(ctx.businessId()), ctx.accessToken())
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        UUID methodId = readUuid(listJson, "$[0].id");

        patch(base(ctx.businessId()) + "/" + methodId + "/toggle?active=false", ctx.accessToken(), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive").value(false));

        // Disabled method no longer shows in the active list.
        get(base(ctx.businessId()), ctx.accessToken())
                .andExpect(jsonPath("$[?(@.id=='" + methodId + "')]").doesNotExist());

        patch(base(ctx.businessId()) + "/" + methodId + "/toggle?active=true", ctx.accessToken(), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive").value(true));
    }

    private UUID idByType(String json, String type) throws Exception {
        for (com.fasterxml.jackson.databind.JsonNode n : objectMapper.readTree(json)) {
            if (type.equals(n.get("type").asText())) {
                return UUID.fromString(n.get("id").asText());
            }
        }
        return null;
    }

    @Test
    void deleteMethod_systemMethodForbidden() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        String listJson = get(base(ctx.businessId()), ctx.accessToken())
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        UUID cashId = idByType(listJson, "CASH");

        assertProblemDetail(delete(base(ctx.businessId()) + "/" + cashId, ctx.accessToken()),
                400, "SYSTEM_METHOD_DELETE");
    }

    @Test
    void deleteMethod_customMethod() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        String createJson = createMethod(ctx, "QRCODE", "QR Code")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        UUID methodId = readUuid(createJson, "$.id");

        assertSuccess(delete(base(ctx.businessId()) + "/" + methodId, ctx.accessToken()),
                "Payment method deleted");

        assertProblemDetail(get(base(ctx.businessId()) + "/" + methodId, ctx.accessToken()),
                400, "METHOD_NOT_FOUND");
    }

    @Test
    void deleteMethod_notFound() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertProblemDetail(delete(base(ctx.businessId()) + "/" + UUID.randomUUID(), ctx.accessToken()),
                400, "METHOD_NOT_FOUND");
    }
}
