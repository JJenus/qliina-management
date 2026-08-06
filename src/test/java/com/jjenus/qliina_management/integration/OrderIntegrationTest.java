package com.jjenus.qliina_management.integration;

import com.jjenus.qliina_management.business.service.ServiceCatalogService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Order suite: OrderController — /api/v1/{businessId}/orders.
 */
class OrderIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private ServiceCatalogService catalogService;

    private String orderBase(UUID businessId) {
        return "/api/v1/" + businessId + "/orders";
    }

    private String newPhone() {
        return "+1" + (555_700_0000L + counter.incrementAndGet());
    }

    /** Creates a customer and returns its id. */
    private UUID createCustomer(AuthContext ctx) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("firstName", "Oba");
        body.put("lastName", "Order");
        body.put("phone", newPhone());
        String json = post("/api/v1/" + ctx.businessId() + "/customers", ctx.accessToken(), body)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return readUuid(json, "$.id");
    }

    private UUID serviceTypeId(AuthContext ctx) {
        return catalogService.getActiveServices(ctx.businessId()).get(0).getId();
    }

    private UUID garmentTypeId(AuthContext ctx) {
        return catalogService.getActiveGarments(ctx.businessId()).get(0).getId();
    }

    private Map<String, Object> orderBody(UUID customerId, UUID shopId, UUID serviceTypeId, UUID garmentTypeId) {
        Map<String, Object> body = new HashMap<>();
        body.put("customerId", customerId.toString());
        body.put("shopId", shopId.toString());
        body.put("items", List.of(Map.of(
                "serviceTypeId", serviceTypeId.toString(),
                "garmentTypeId", garmentTypeId.toString(),
                "quantity", 2,
                "unitPrice", 3.50,
                "description", "Two shirts")));
        return body;
    }

    /** Creates an order and returns the created id. */
    private UUID createOrder(AuthContext ctx) throws Exception {
        UUID custId = createCustomer(ctx);
        Map<String, Object> body = orderBody(custId, ctx.shopId(), serviceTypeId(ctx), garmentTypeId(ctx));
        ResultActions rs = post(orderBase(ctx.businessId()), ctx.accessToken(), body);
        rs.andExpect(status().isOk());
        return extractUuid(rs.andReturn(), "$.id");
    }

    // ---------------------------------------------------------------------
    // Create
    // ---------------------------------------------------------------------

    @Test
    void createOrder_fullFlow() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        UUID custId = createCustomer(ctx);
        Map<String, Object> body = orderBody(custId, ctx.shopId(), serviceTypeId(ctx), garmentTypeId(ctx));

        ResultActions rs = post(orderBase(ctx.businessId()), ctx.accessToken(), body);
        rs.andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECEIVED"))
                .andExpect(jsonPath("$.priority").value("NORMAL"))
                .andExpect(jsonPath("$.itemCount").value(2))
                .andExpect(jsonPath("$.totalAmount").value(7.0))
                .andExpect(jsonPath("$.paidAmount").value(0))
                .andExpect(jsonPath("$.balanceDue").value(7.0))
                .andExpect(jsonPath("$.customer.id").value(custId.toString()))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.items[0].status").value("RECEIVED"))
                .andExpect(jsonPath("$.timeline[0].status").value("RECEIVED"));

        String json = rs.andReturn().getResponse().getContentAsString();
        UUID orderId = readUuid(json, "$.id");
        String tracking = readString(json, "$.trackingNumber");

        // Fetch by id and by tracking number.
        get(orderBase(ctx.businessId()) + "/" + orderId, ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId.toString()))
                .andExpect(jsonPath("$.orderNumber").exists());
        get(orderBase(ctx.businessId()) + "/tracking/" + tracking, ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId.toString()));
    }

    @Test
    void createOrder_validation_requiredFields() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertValidation(post(orderBase(ctx.businessId()), ctx.accessToken(), Map.of()),
                Map.of(
                        "customerId", "Customer ID is required",
                        "shopId", "Shop ID is required",
                        "items", "Items are required"));
    }

    @Test
    void createOrder_missingCustomer() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        Map<String, Object> body = orderBody(UUID.randomUUID(), ctx.shopId(), serviceTypeId(ctx), garmentTypeId(ctx));
        assertProblemDetail(post(orderBase(ctx.businessId()), ctx.accessToken(), body),
                400, "CUSTOMER_NOT_FOUND");
    }

    @Test
    void createOrder_withDiscountAndPriority() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        UUID custId = createCustomer(ctx);
        Map<String, Object> body = orderBody(custId, ctx.shopId(), serviceTypeId(ctx), garmentTypeId(ctx));
        body.put("priority", "EXPRESS");
        body.put("discounts", List.of(Map.of("type", "PERCENTAGE", "value", 10.0, "reason", "VIP")));

        post(orderBase(ctx.businessId()), ctx.accessToken(), body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priority").value("EXPRESS"))
                .andExpect(jsonPath("$.totalAmount").value(6.3)); // 7.0 - 10%
    }

    // ---------------------------------------------------------------------
    // List / get
    // ---------------------------------------------------------------------

    @Test
    void listOrders_afterCreate() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        createOrder(ctx);

        get(orderBase(ctx.businessId()), ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].status").value("RECEIVED"));
    }

    @Test
    void listOrders_requiresAuth() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        get(orderBase(ctx.businessId()), null).andExpect(status().isUnauthorized());
    }

    @Test
    void listOrders_crossTenantDenied() throws Exception {
        AuthContext a = registerBusinessAndOwner();
        AuthContext b = registerBusinessAndOwner();
        get(orderBase(b.businessId()), a.accessToken())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    void getOrder_notFound() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertProblemDetail(get(orderBase(ctx.businessId()) + "/" + UUID.randomUUID(), ctx.accessToken()),
                400, "ORDER_NOT_FOUND");
    }

    @Test
    void getOrderByTracking_notFound() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertProblemDetail(get(orderBase(ctx.businessId()) + "/tracking/TRK-UNKNOWN", ctx.accessToken()),
                400, "ORDER_NOT_FOUND");
    }

    // ---------------------------------------------------------------------
    // Quick order
    // ---------------------------------------------------------------------

    @Test
    void quickOrder_createsGuestCustomer() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        Map<String, Object> body = new HashMap<>();
        body.put("customerPhone", newPhone());
        body.put("shopId", ctx.shopId().toString());
        body.put("serviceType", "Wash & Fold");
        body.put("itemCount", 3);
        body.put("totalAmount", 10.5);
        body.put("notes", "Quick");

        post(orderBase(ctx.businessId()) + "/quick", ctx.accessToken(), body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECEIVED"))
                .andExpect(jsonPath("$.itemCount").value(3))
                .andExpect(jsonPath("$.customer.name").value("Guest Customer"))
                .andExpect(jsonPath("$.notes[0].content").value("Quick"));

        // The guest customer now exists.
        get("/api/v1/" + ctx.businessId() + "/customers", ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void quickOrder_validation_requiredFields() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertValidation(post(orderBase(ctx.businessId()) + "/quick", ctx.accessToken(), Map.of()),
                Map.of(
                        "customerPhone", "Customer phone is required",
                        "shopId", "Shop ID is required",
                        "serviceType", "Service type is required",
                        "itemCount", "Item count is required",
                        "totalAmount", "Total amount is required"));
    }

    // ---------------------------------------------------------------------
    // Update / cancel
    // ---------------------------------------------------------------------

    @Test
    void updateOrder_priorityAndTags() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        UUID orderId = createOrder(ctx);
        Map<String, Object> body = Map.of("priority", "URGENT", "tags", List.of("rush"));

        put(orderBase(ctx.businessId()) + "/" + orderId, ctx.accessToken(), body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priority").value("URGENT"))
                .andExpect(jsonPath("$.tags[0]").value("rush"));
    }

    @Test
    void updateOrder_notFound() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertProblemDetail(put(orderBase(ctx.businessId()) + "/" + UUID.randomUUID(),
                ctx.accessToken(), Map.of("priority", "NORMAL")), 400, "ORDER_NOT_FOUND");
    }

    @Test
    void cancelOrder_success() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        UUID orderId = createOrder(ctx);
        assertSuccess(delete(orderBase(ctx.businessId()) + "/" + orderId, ctx.accessToken(),
                Map.of("reason", "Customer changed mind")), "Order cancelled successfully");

        get(orderBase(ctx.businessId()) + "/" + orderId, ctx.accessToken())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void cancelOrder_notFound() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertProblemDetail(delete(orderBase(ctx.businessId()) + "/" + UUID.randomUUID(),
                ctx.accessToken(), Map.of("reason", "r")), 400, "ORDER_NOT_FOUND");
    }

    @Test
    void cancelOrder_completedForbidden() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        UUID orderId = createOrder(ctx);
        advanceToCompleted(ctx, orderId);
        assertProblemDetail(delete(orderBase(ctx.businessId()) + "/" + orderId, ctx.accessToken(),
                Map.of("reason", "Late cancel")), 400, "ORDER_ALREADY_COMPLETED");
    }

    // ---------------------------------------------------------------------
    // Status workflow
    // ---------------------------------------------------------------------

    private void advanceToCompleted(AuthContext ctx, UUID orderId) throws Exception {
        String base = orderBase(ctx.businessId()) + "/" + orderId + "/status";
        for (String status : List.of("WASHING", "WASHED", "IRONING", "IRONED", "QUALITY_CHECK",
                "READY_FOR_PICKUP", "COMPLETED")) {
            post(base, ctx.accessToken(), Map.of("status", status))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.currentStatus").value(status));
        }
    }

    @Test
    void statusFlow_toCompleted() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        UUID orderId = createOrder(ctx);

        post(orderBase(ctx.businessId()) + "/" + orderId + "/status", ctx.accessToken(), Map.of("status", "WASHING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.previousStatus").value("RECEIVED"))
                .andExpect(jsonPath("$.currentStatus").value("WASHING"))
                .andExpect(jsonPath("$.updatedBy").exists());

        for (String status : List.of("WASHED", "IRONING", "IRONED", "QUALITY_CHECK",
                "READY_FOR_PICKUP", "COMPLETED")) {
            post(orderBase(ctx.businessId()) + "/" + orderId + "/status", ctx.accessToken(), Map.of("status", status))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.currentStatus").value(status));
        }

        get(orderBase(ctx.businessId()) + "/" + orderId, ctx.accessToken())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.completedAt").exists())
                .andExpect(jsonPath("$.items[0].status").value("COMPLETED"));
    }

    @Test
    void updateOrderStatus_invalidTransition() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        UUID orderId = createOrder(ctx);
        assertProblemDetail(post(orderBase(ctx.businessId()) + "/" + orderId + "/status",
                ctx.accessToken(), Map.of("status", "COMPLETED")), 400, "INVALID_STATUS_TRANSITION");
    }

    @Test
    void updateOrderStatus_validation_blankStatus() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        UUID orderId = createOrder(ctx);
        assertValidation(post(orderBase(ctx.businessId()) + "/" + orderId + "/status",
                ctx.accessToken(), Map.of()), Map.of("status", "Status is required"));
    }

    @Test
    void updateItemStatus_forwardAndBackwardBlocked() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        Map<String, Object> body = orderBody(createCustomer(ctx), ctx.shopId(), serviceTypeId(ctx), garmentTypeId(ctx));
        String json = post(orderBase(ctx.businessId()), ctx.accessToken(), body)
                .andReturn().getResponse().getContentAsString();
        UUID orderId = readUuid(json, "$.id");
        UUID itemId = readUuid(json, "$.items[0].id");

        post(orderBase(ctx.businessId()) + "/" + orderId + "/items/" + itemId + "/status",
                ctx.accessToken(), Map.of("status", "WASHING", "itemId", itemId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStatus").value("WASHING"));

        assertProblemDetail(post(orderBase(ctx.businessId()) + "/" + orderId + "/items/" + itemId + "/status",
                ctx.accessToken(), Map.of("status", "RECEIVED", "itemId", itemId.toString())),
                400, "INVALID_ITEM_STATUS_TRANSITION");
    }

    @Test
    void updateItemStatus_itemNotFound() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        UUID orderId = createOrder(ctx);
        assertProblemDetail(post(orderBase(ctx.businessId()) + "/" + orderId + "/items/" + UUID.randomUUID() + "/status",
                ctx.accessToken(), Map.of("status", "WASHING", "itemId", UUID.randomUUID().toString())),
                400, "ITEM_NOT_FOUND");
    }

    // ---------------------------------------------------------------------
    // Timeline & notes
    // ---------------------------------------------------------------------

    @Test
    void getOrderTimeline_afterCreate() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        UUID orderId = createOrder(ctx);
        get(orderBase(ctx.businessId()) + "/" + orderId + "/timeline", ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("RECEIVED"))
                .andExpect(jsonPath("$[0].type").value("STATUS_CHANGE"));
    }

    @Test
    void addOrderNote_success() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        UUID orderId = createOrder(ctx);
        post(orderBase(ctx.businessId()) + "/" + orderId + "/notes", ctx.accessToken(),
                Map.of("content", "Call customer first", "type", "INTERNAL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("Call customer first"))
                .andExpect(jsonPath("$.type").value("INTERNAL"));
    }

    @Test
    void addOrderNote_validation() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        UUID orderId = createOrder(ctx);
        assertValidation(post(orderBase(ctx.businessId()) + "/" + orderId + "/notes",
                ctx.accessToken(), Map.of()), Map.of("content", "Content is required"));
    }

    // ---------------------------------------------------------------------
    // Transfer
    // ---------------------------------------------------------------------

    @Test
    void transferOrder_toAnotherShop() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        UUID orderId = createOrder(ctx);

        // Upgrade to STARTER (maxShops=3) so a second shop can be created.
        post("/api/v1/" + ctx.businessId() + "/subscription/change-plan", ctx.accessToken(),
                Map.of("plan", "STARTER"))
                .andExpect(status().isOk());

        String shopJson = post("/api/v1/" + ctx.businessId() + "/shops", ctx.accessToken(),
                Map.of("name", "Branch Two", "code", "SH" + random().toUpperCase() + "B"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID secondShopId = readUuid(shopJson, "$.id");

        post(orderBase(ctx.businessId()) + "/" + orderId + "/transfer", ctx.accessToken(),
                Map.of("targetShopId", secondShopId.toString(), "reason", "Re-routing"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shop.id").value(secondShopId.toString()));

        get(orderBase(ctx.businessId()) + "/" + orderId + "/timeline", ctx.accessToken())
                .andExpect(jsonPath("$[*].type", hasItem("TRANSFER")));
    }

    @Test
    void transferOrder_validation() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        UUID orderId = createOrder(ctx);
        assertValidation(post(orderBase(ctx.businessId()) + "/" + orderId + "/transfer",
                ctx.accessToken(), Map.of()),
                Map.of(
                        "targetShopId", "Target shop ID is required",
                        "reason", "Reason is required"));
    }

    // ---------------------------------------------------------------------
    // Analytics & counts
    // ---------------------------------------------------------------------

    @Test
    void getDailySummary_reflectsOrders() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        createOrder(ctx);
        String date = LocalDateTime.now().toString();
        get(orderBase(ctx.businessId()) + "/daily-summary?date=" + date, ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalOrders").value(1))
                .andExpect(jsonPath("$.pendingOrders").value(1))
                .andExpect(jsonPath("$.byServiceType").isArray())
                .andExpect(jsonPath("$.byHour").isArray());
    }

    @Test
    void countPendingOrders_afterCreate() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        createOrder(ctx);
        get(orderBase(ctx.businessId()) + "/counts/pending", ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(1));
    }

    @Test
    void countOrdersByDateRange() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        createOrder(ctx);
        LocalDateTime now = LocalDateTime.now();
        get(orderBase(ctx.businessId()) + "/counts/by-date-range?startDate=" + now.minusDays(1)
                + "&endDate=" + now.plusDays(1), ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(1));
    }

    // ---------------------------------------------------------------------
    // Attachments (stubs) & returns & bulk
    // ---------------------------------------------------------------------

    @Test
    void uploadAttachment_stub() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        UUID orderId = createOrder(ctx);
        MockMultipartFile file = new MockMultipartFile(
                "file", "receipt.png", "image/png", new byte[]{1, 2, 3});
        mockMvc.perform(MockMvcRequestBuilders.multipart(orderBase(ctx.businessId()) + "/" + orderId + "/attachments")
                        .file(file)
                        .param("type", "RECEIPT")
                        .header("Authorization", "Bearer " + ctx.accessToken()))
                .andExpect(status().isOk());
    }

    @Test
    void deleteAttachment_stub() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        UUID orderId = createOrder(ctx);
        assertSuccess(delete(orderBase(ctx.businessId()) + "/" + orderId + "/attachments/" + UUID.randomUUID(),
                ctx.accessToken()), "Attachment deleted successfully");
    }

    @Test
    void returnOrder_invalidState() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        UUID orderId = createOrder(ctx);
        assertProblemDetail(post(orderBase(ctx.businessId()) + "/" + orderId + "/return",
                ctx.accessToken(), Map.of("reason", "Damaged")), 400, "INVALID_ORDER_STATE");
    }

    @Test
    void returnOrder_onCompleted() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        UUID orderId = createOrder(ctx);
        advanceToCompleted(ctx, orderId);

        post(orderBase(ctx.businessId()) + "/" + orderId + "/return", ctx.accessToken(),
                Map.of("reason", "Wrong size", "refundRequested", true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RETURNED"))
                .andExpect(jsonPath("$.items[0].status").value("ISSUE_REPORTED"))
                .andExpect(jsonPath("$.completedAt").doesNotExist());
    }

    @Test
    void bulkStatusUpdate_stub() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        UUID orderId = createOrder(ctx);
        assertSuccess(post(orderBase(ctx.businessId()) + "/bulk/status", ctx.accessToken(),
                Map.of("orderIds", new ArrayList<>(List.of(orderId.toString())), "status", "WASHING")),
                "Bulk status update completed");
    }
}
