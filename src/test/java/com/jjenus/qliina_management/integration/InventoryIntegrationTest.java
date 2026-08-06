package com.jjenus.qliina_management.integration;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Inventory suite: InventoryController — /api/v1/{businessId}/inventory.
 * (Items, shop stock, adjustments/transfers, suppliers, purchase orders.)
 */
class InventoryIntegrationTest extends BaseIntegrationTest {

    private String base(UUID businessId) {
        return "/api/v1/" + businessId + "/inventory";
    }

    private Map<String, Object> itemBody(String sku) {
        Map<String, Object> body = new HashMap<>();
        body.put("sku", sku);
        body.put("name", "Laundry Detergent");
        body.put("category", "DETERGENT");
        body.put("unit", "LITER");
        body.put("reorderLevel", 5);
        body.put("reorderQuantity", 10);
        body.put("unitPrice", 2.50);
        return body;
    }

    /** Creates an inventory item and returns its id. */
    private UUID createItem(AuthContext ctx) throws Exception {
        String sku = "SKU" + random().toUpperCase();
        String json = post(base(ctx.businessId()) + "/items", ctx.accessToken(), itemBody(sku))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return readUuid(json, "$.id");
    }

    private UUID createSupplier(AuthContext ctx) throws Exception {
        String json = post(base(ctx.businessId()) + "/suppliers", ctx.accessToken(),
                Map.of("name", "ChemCo Supplies"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return readUuid(json, "$.id");
    }

    private Map<String, Object> adjustBody(AuthContext ctx, UUID itemId, int qty, String reason) {
        return Map.of(
                "shopId", ctx.shopId().toString(),
                "adjustments", List.of(Map.of(
                        "itemId", itemId.toString(),
                        "quantity", qty,
                        "reason", reason)));
    }

    // ---------------------------------------------------------------------
    // Items
    // ---------------------------------------------------------------------

    @Test
    void listItems_emptyInitially() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        get(base(ctx.businessId()) + "/items", ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void createAndGetItem() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        UUID itemId = createItem(ctx);

        get(base(ctx.businessId()) + "/items/" + itemId, ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(itemId.toString()))
                .andExpect(jsonPath("$.name").value("Laundry Detergent"))
                .andExpect(jsonPath("$.category").value("DETERGENT"))
                .andExpect(jsonPath("$.isActive").value(true));

        get(base(ctx.businessId()) + "/items", ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void createItem_duplicateSku() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        String sku = "SKU" + random().toUpperCase();
        post(base(ctx.businessId()) + "/items", ctx.accessToken(), itemBody(sku)).andExpect(status().isOk());

        assertProblemDetail(post(base(ctx.businessId()) + "/items", ctx.accessToken(), itemBody(sku)),
                400, "Business Rule Violation", "DUPLICATE_SKU");
    }

    @Test
    void createItem_validation() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertValidation(post(base(ctx.businessId()) + "/items", ctx.accessToken(), Map.of()),
                Map.of(
                        "sku", "SKU is required",
                        "name", "Name is required",
                        "category", "Category is required",
                        "unit", "Unit is required",
                        "reorderLevel", "Reorder level is required",
                        "reorderQuantity", "Reorder quantity is required"));
    }

    @Test
    void updateItem_success() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        UUID itemId = createItem(ctx);
        put(base(ctx.businessId()) + "/items/" + itemId, ctx.accessToken(),
                Map.of("name", "Eco Detergent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(itemId.toString()))
                .andExpect(jsonPath("$.name").value("Eco Detergent"));
    }

    @Test
    void getItem_notFound() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertProblemDetail(get(base(ctx.businessId()) + "/items/" + UUID.randomUUID(), ctx.accessToken()),
                400, "ITEM_NOT_FOUND");
    }

    // ---------------------------------------------------------------------
    // Shop stock & adjustments
    // ---------------------------------------------------------------------

    @Test
    void getShopStock_afterCreate() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        createItem(ctx);
        get(base(ctx.businessId()) + "/stock/" + ctx.shopId(), ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].items.length()").value(1))
                .andExpect(jsonPath("$[0].items[0].quantity").value(0.0));
    }

    @Test
    void adjustStock_addAndListTransactions() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        UUID itemId = createItem(ctx);

        post(base(ctx.businessId()) + "/stock/adjust", ctx.accessToken(),
                adjustBody(ctx, itemId, 10, "RECEIVED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shopId").value(ctx.shopId().toString()))
                .andExpect(jsonPath("$.results[0].success").value(true))
                .andExpect(jsonPath("$.results[0].newQuantity").value(10.0));

        get(base(ctx.businessId()) + "/transactions", ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].type").value("RECEIVED"))
                .andExpect(jsonPath("$.content[0].itemId").value(itemId.toString()));

        get(base(ctx.businessId()) + "/stock/" + ctx.shopId(), ctx.accessToken())
                .andExpect(jsonPath("$[0].items[0].quantity").value(10.0));
    }

    @Test
    void adjustStock_remove() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        UUID itemId = createItem(ctx);
        post(base(ctx.businessId()) + "/stock/adjust", ctx.accessToken(),
                adjustBody(ctx, itemId, 10, "RECEIVED")).andExpect(status().isOk());

        post(base(ctx.businessId()) + "/stock/adjust", ctx.accessToken(),
                adjustBody(ctx, itemId, -4, "USED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].success").value(true))
                .andExpect(jsonPath("$.results[0].newQuantity").value(6.0));
    }

    @Test
    void adjustStock_unknownItem_reportsFailure() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        post(base(ctx.businessId()) + "/stock/adjust", ctx.accessToken(),
                adjustBody(ctx, UUID.randomUUID(), 5, "RECEIVED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].success").value(false));
    }

    @Test
    void adjustStock_insufficientRemove_reportsFailure() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        UUID itemId = createItem(ctx);
        post(base(ctx.businessId()) + "/stock/adjust", ctx.accessToken(),
                adjustBody(ctx, itemId, -5, "USED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].success").value(false));
    }

    @Test
    void adjustStock_validation() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertValidation(post(base(ctx.businessId()) + "/stock/adjust", ctx.accessToken(), Map.of()),
                Map.of(
                        "shopId", "Shop ID is required",
                        "adjustments", "Adjustments are required"));
    }

    @Test
    void transferStock_betweenShops() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        UUID itemId = createItem(ctx);

        // Top up stock in the main shop.
        post(base(ctx.businessId()) + "/stock/adjust", ctx.accessToken(),
                adjustBody(ctx, itemId, 10, "RECEIVED")).andExpect(status().isOk());

        // Create a second shop.
        post("/api/v1/" + ctx.businessId() + "/subscription/change-plan", ctx.accessToken(),
                Map.of("plan", "STARTER")).andExpect(status().isOk());
        String shopJson = post("/api/v1/" + ctx.businessId() + "/shops", ctx.accessToken(),
                Map.of("name", "Branch Two", "code", "SH" + random().toUpperCase() + "B"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID secondShopId = readUuid(shopJson, "$.id");

        Map<String, Object> body = new HashMap<>();
        body.put("sourceShopId", ctx.shopId().toString());
        body.put("targetShopId", secondShopId.toString());
        body.put("itemId", itemId.toString());
        body.put("quantity", 4.0);
        assertSuccess(post(base(ctx.businessId()) + "/stock/transfer", ctx.accessToken(), body),
                "Stock transferred successfully");

        // Source shop now has 6, target has 4.
        get(base(ctx.businessId()) + "/stock/" + ctx.shopId(), ctx.accessToken())
                .andExpect(jsonPath("$[0].items[0].quantity").value(6.0));
        get(base(ctx.businessId()) + "/stock/" + secondShopId, ctx.accessToken())
                .andExpect(jsonPath("$[0].items[0].quantity").value(4.0));
    }

    @Test
    void transferStock_insufficientStock() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        UUID itemId = createItem(ctx);
        assertProblemDetail(post(base(ctx.businessId()) + "/stock/transfer", ctx.accessToken(),
                Map.of("sourceShopId", ctx.shopId().toString(), "targetShopId", UUID.randomUUID().toString(),
                        "itemId", itemId.toString(), "quantity", 5.0)),
                400, "INSUFFICIENT_STOCK");
    }

    @Test
    void transferStock_sourceShopMissingItem() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertProblemDetail(post(base(ctx.businessId()) + "/stock/transfer", ctx.accessToken(),
                Map.of("sourceShopId", ctx.shopId().toString(), "targetShopId", UUID.randomUUID().toString(),
                        "itemId", UUID.randomUUID().toString(), "quantity", 5.0)),
                400, "ITEM_NOT_FOUND_IN_SOURCE");
    }

    @Test
    void transferStock_validation() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertValidation(post(base(ctx.businessId()) + "/stock/transfer", ctx.accessToken(), Map.of()),
                Map.of(
                        "sourceShopId", "Source shop ID is required",
                        "targetShopId", "Target shop ID is required",
                        "itemId", "Item ID is required",
                        "quantity", "Quantity is required"));
    }

    // ---------------------------------------------------------------------
    // Alerts
    // ---------------------------------------------------------------------

    @Test
    void getStockAlerts_empty() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        createItem(ctx);
        get(base(ctx.businessId()) + "/alerts", ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void acknowledgeAlert_notFound() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertProblemDetail(post(base(ctx.businessId()) + "/alerts/" + UUID.randomUUID() + "/acknowledge",
                ctx.accessToken(), null), 400, "ALERT_NOT_FOUND");
    }

    @Test
    void resolveAlert_notFound() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertProblemDetail(post(base(ctx.businessId()) + "/alerts/" + UUID.randomUUID() + "/resolve",
                ctx.accessToken(), null), 400, "ALERT_NOT_FOUND");
    }

    // ---------------------------------------------------------------------
    // Suppliers
    // ---------------------------------------------------------------------

    @Test
    void createAndGetSupplier() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        UUID supplierId = createSupplier(ctx);

        get(base(ctx.businessId()) + "/suppliers/" + supplierId, ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(supplierId.toString()))
                .andExpect(jsonPath("$.name").value("ChemCo Supplies"));

        get(base(ctx.businessId()) + "/suppliers", ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void updateSupplier_success() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        UUID supplierId = createSupplier(ctx);
        put(base(ctx.businessId()) + "/suppliers/" + supplierId, ctx.accessToken(),
                Map.of("name", "EcoChem", "paymentTerms", "NET30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("EcoChem"))
                .andExpect(jsonPath("$.paymentTerms").value("NET30"));
    }

    @Test
    void getSupplier_notFound() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertProblemDetail(get(base(ctx.businessId()) + "/suppliers/" + UUID.randomUUID(), ctx.accessToken()),
                400, "SUPPLIER_NOT_FOUND");
    }

    @Test
    void createSupplier_validation() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertValidation(post(base(ctx.businessId()) + "/suppliers", ctx.accessToken(), Map.of()),
                Map.of("name", "Supplier name is required"));
    }

    // ---------------------------------------------------------------------
    // Purchase orders
    // ---------------------------------------------------------------------

    private Map<String, Object> poBody(AuthContext ctx, UUID supplierId, UUID itemId, int qty) {
        return Map.of(
                "supplierId", supplierId.toString(),
                "shopId", ctx.shopId().toString(),
                "items", List.of(Map.of(
                        "itemId", itemId.toString(),
                        "quantity", qty,
                        "unitPrice", 2.00)));
    }

    @Test
    void createPurchaseOrder_fullFlow() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        UUID supplierId = createSupplier(ctx);
        UUID itemId = createItem(ctx);

        // Create (DRAFT).
        String json = post(base(ctx.businessId()) + "/purchase-orders", ctx.accessToken(),
                poBody(ctx, supplierId, itemId, 5))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.poNumber").exists())
                .andExpect(jsonPath("$.items[0].quantity").value(5))
                .andReturn().getResponse().getContentAsString();
        UUID poId = readUuid(json, "$.id");

        // Approve.
        patch(base(ctx.businessId()) + "/purchase-orders/" + poId + "/status", ctx.accessToken(),
                Map.of("status", "APPROVED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.approvedBy").exists());

        // Receive → stock lands in the shop.
        post(base(ctx.businessId()) + "/purchase-orders/" + poId + "/receive", ctx.accessToken(),
                Map.of("items", List.of(Map.of("itemId", itemId.toString(), "receivedQuantity", 5))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECEIVED"));

        get(base(ctx.businessId()) + "/stock/" + ctx.shopId(), ctx.accessToken())
                .andExpect(jsonPath("$[0].items[0].quantity").value(5.0));

        get(base(ctx.businessId()) + "/purchase-orders", ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void createPurchaseOrder_supplierNotFound() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        UUID itemId = createItem(ctx);
        assertProblemDetail(post(base(ctx.businessId()) + "/purchase-orders", ctx.accessToken(),
                poBody(ctx, UUID.randomUUID(), itemId, 1)), 400, "SUPPLIER_NOT_FOUND");
    }

    @Test
    void createPurchaseOrder_itemNotFound() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        UUID supplierId = createSupplier(ctx);
        assertProblemDetail(post(base(ctx.businessId()) + "/purchase-orders", ctx.accessToken(),
                poBody(ctx, supplierId, UUID.randomUUID(), 1)), 400, "ITEM_NOT_FOUND");
    }

    @Test
    void createPurchaseOrder_validation() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertValidation(post(base(ctx.businessId()) + "/purchase-orders", ctx.accessToken(), Map.of()),
                Map.of(
                        "supplierId", "Supplier ID is required",
                        "shopId", "Shop ID is required",
                        "items", "Items are required"));
    }

    @Test
    void getPurchaseOrder_notFound() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertProblemDetail(get(base(ctx.businessId()) + "/purchase-orders/" + UUID.randomUUID(),
                ctx.accessToken()), 400, "PO_NOT_FOUND");
    }

    @Test
    void updatePurchaseOrderStatus_notFound() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertProblemDetail(patch(base(ctx.businessId()) + "/purchase-orders/" + UUID.randomUUID() + "/status",
                ctx.accessToken(), Map.of("status", "APPROVED")), 400, "PO_NOT_FOUND");
    }

    @Test
    void receivePurchaseOrder_partialThenFull() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        UUID supplierId = createSupplier(ctx);
        UUID itemId = createItem(ctx);
        String poJson = post(base(ctx.businessId()) + "/purchase-orders", ctx.accessToken(),
                poBody(ctx, supplierId, itemId, 10))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        UUID poId = readUuid(poJson, "$.id");

        // Receive half → PARTIALLY_RECEIVED.
        post(base(ctx.businessId()) + "/purchase-orders/" + poId + "/receive", ctx.accessToken(),
                Map.of("items", List.of(Map.of("itemId", itemId.toString(), "receivedQuantity", 5))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PARTIALLY_RECEIVED"));

        // Receive the rest → RECEIVED.
        post(base(ctx.businessId()) + "/purchase-orders/" + poId + "/receive", ctx.accessToken(),
                Map.of("items", List.of(Map.of("itemId", itemId.toString(), "receivedQuantity", 5))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECEIVED"));
    }
}
