package com.jjenus.qliina_management.integration;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Worker order suite: WorkerOrderController — /api/v1/{businessId}/worker/orders.
 * BUSINESS_ADMIN exercises these; the worker endpoints only demand order.view and
 * are not clock-in-gated for non-worker roles.
 */
class WorkerOrderIntegrationTest extends BaseIntegrationTest {

    private String workerBase(UUID businessId) {
        return "/api/v1/" + businessId + "/worker/orders";
    }

    private UUID createItem(AuthContext ctx) throws Exception {
        String custJson = post("/api/v1/" + ctx.businessId() + "/customers", ctx.accessToken(),
                Map.of("firstName", "Work", "lastName", "Queue", "phone", "+1" + (555_900_0000L + counter.incrementAndGet())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        UUID custId = readUuid(custJson, "$.id");

        Map<String, Object> body = new HashMap<>();
        body.put("customerId", custId.toString());
        body.put("shopId", ctx.shopId().toString());
        body.put("items", List.of(Map.of(
                "serviceTypeId", "00000000-0000-0000-0000-000000000002",
                "quantity", 1,
                "unitPrice", 4.0)));
        String orderJson = post("/api/v1/" + ctx.businessId() + "/orders", ctx.accessToken(), body)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return readUuid(orderJson, "$.items[0].id");
    }

    @Test
    void lookupItem_found() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        UUID itemId = createItem(ctx);

        get(workerBase(ctx.businessId()) + "/items/lookup/" + itemId, ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(itemId.toString()));
    }

    @Test
    void lookupItem_notFound() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertProblemDetail(get(workerBase(ctx.businessId()) + "/items/lookup/" + UUID.randomUUID(),
                ctx.accessToken()), 400, "ITEM_NOT_FOUND");
    }

    @Test
    void getWorkQueue_emptyForAdminRole() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        get(workerBase(ctx.businessId()) + "/items/queue", ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void getWorkHistory_returnsPage() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        get(workerBase(ctx.businessId()) + "/items/history", ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }
}
