package com.jjenus.qliina_management.integration;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Public order tracking suite: PublicOrderController — /api/v1/public/orders.
 * These endpoints are intentionally unauthenticated (permitAll in SecurityConfig).
 */
class PublicOrderIntegrationTest extends BaseIntegrationTest {

    private Map<String, Object> orderBody(UUID customerId, UUID shopId) {
        Map<String, Object> body = new HashMap<>();
        body.put("customerId", customerId.toString());
        body.put("shopId", shopId.toString());
        body.put("items", List.of(Map.of(
                "serviceTypeId", "00000000-0000-0000-0000-000000000001",
                "quantity", 1,
                "unitPrice", 5.0)));
        return body;
    }

    @Test
    void trackOrder_withoutAuth_returnsPublicInfo() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();

        String custJson = post("/api/v1/" + ctx.businessId() + "/customers", ctx.accessToken(),
                Map.of("firstName", "Pub", "lastName", "Track", "phone", "+1" + (555_800_0000L + counter.incrementAndGet())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        UUID custId = readUuid(custJson, "$.id");

        String orderJson = post("/api/v1/" + ctx.businessId() + "/orders", ctx.accessToken(),
                orderBody(custId, ctx.shopId()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String tracking = readString(orderJson, "$.trackingNumber");

        // No token at all.
        get("/api/v1/public/orders/track/" + tracking, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackingNumber").value(tracking))
                .andExpect(jsonPath("$.status").value("RECEIVED"))
                .andExpect(jsonPath("$.items[0].quantity").value(1))
                .andExpect(jsonPath("$.recentTimeline").isArray());
    }

    @Test
    void trackOrder_unknownTracking() throws Exception {
        assertProblemDetail(get("/api/v1/public/orders/track/TRK-UNKNOWN-1", null),
                400, "ORDER_NOT_FOUND");
    }
}
