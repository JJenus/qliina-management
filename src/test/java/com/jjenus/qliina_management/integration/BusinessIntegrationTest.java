package com.jjenus.qliina_management.integration;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the business module: Business metadata, subscription
 * plans, shops, and the service catalog. Asserts status codes, error contracts,
 * and result contents.
 */
class BusinessIntegrationTest extends BaseIntegrationTest {

    private void changeToStarter(AuthContext ctx) throws Exception {
        post("/api/v1/" + ctx.businessId() + "/subscription/change-plan", ctx.accessToken(),
                Map.of("plan", "STARTER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan").value("STARTER"));
    }

    // ---------------------------------------------------------------------
    // Business metadata
    // ---------------------------------------------------------------------

    @Test
    void getBusiness_ownerReadsOwnBusiness() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        get("/api/v1/businesses/" + ctx.businessId(), ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ctx.businessId().toString()))
                .andExpect(jsonPath("$.status").value("TRIAL"))
                .andExpect(jsonPath("$.plan").value("FREE"))
                .andExpect(jsonPath("$.activeShopCount").value(1));
    }

    @Test
    void getBusiness_nonExistent_permissionDeniedBeforeLookup() throws Exception {
        // Divergence from docs: hasPermission(businessId) runs before the service
        // lookup, so a businessId the tenant does not belong to yields 403
        // ACCESS_DENIED (not 400 BUSINESS_NOT_FOUND).
        AuthContext ctx = registerBusinessAndOwner();
        assertProblemDetail(get("/api/v1/businesses/" + UUID.randomUUID(), ctx.accessToken()),
                403, "ACCESS_DENIED");
    }

    @Test
    void updateBusiness_updatesMutableFields() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        put("/api/v1/businesses/" + ctx.businessId(), ctx.accessToken(),
                Map.of("name", "Renamed Laundry", "email", "new_" + random() + "@test.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed Laundry"));
    }

    @Test
    void updateBusiness_invalidEmail_returnsValidationError() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertValidation(put("/api/v1/businesses/" + ctx.businessId(), ctx.accessToken(),
                Map.of("email", "not-an-email")),
                Map.of("email", "must be a well-formed email address"));
    }

    @Test
    void listBusinesses_tenantAdminDenied_superAdminAllowed() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertProblemDetail(get("/api/v1/businesses", ctx.accessToken()), 403, "ACCESS_DENIED");
        get("/api/v1/businesses", adminToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    // ---------------------------------------------------------------------
    // Subscription plans
    // ---------------------------------------------------------------------

    @Test
    void listActivePlans_requiresAuth_documentedDivergence() throws Exception {
        // Documented divergence: endpoint is marked "public" but is NOT in the
        // SecurityConfig whitelist, so it actually requires a JWT.
        get("/api/v1/subscription/plans", null)
                .andExpect(status().isUnauthorized());

        // Billing plans are seeded (Free/Starter/Pro); other tests in this class
        // may have created extra active plans in the shared context, so assert the
        // seeded plans are present rather than an exact count.
        AuthContext ctx = registerBusinessAndOwner();
        get("/api/v1/subscription/plans", ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(3)))
                .andExpect(jsonPath("$[*].name", org.hamcrest.Matchers.hasItems("Free", "Starter", "Pro")));
    }

    @Test
    void getUsageSummary_ownerSeesFreePlanUsage() throws Exception {
        // Fresh businesses are on a 30-day trial with all features enabled, so
        // the usage summary reports unlimited limits (-1) during the trial.
        AuthContext ctx = registerBusinessAndOwner();
        get("/api/v1/" + ctx.businessId() + "/subscription", ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tier").value("FREE"))
                .andExpect(jsonPath("$.status").value("TRIAL"))
                .andExpect(jsonPath("$.activeShops").value(1))
                .andExpect(jsonPath("$.maxShops").value(-1))
                .andExpect(jsonPath("$.activeUsers").value(1))
                .andExpect(jsonPath("$.maxUsers").value(-1))
                .andExpect(jsonPath("$.ordersThisMonth").value(0));
    }

    @Test
    void changePlan_success() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        post("/api/v1/" + ctx.businessId() + "/subscription/change-plan", ctx.accessToken(),
                Map.of("plan", "STARTER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan").value("STARTER"));
    }

    @Test
    void changePlan_samePlan_returnsSamePlan() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertProblemDetail(post("/api/v1/" + ctx.businessId() + "/subscription/change-plan",
                ctx.accessToken(), Map.of("plan", "FREE")), 400, "SAME_PLAN");
    }

    @Test
    void changePlan_invalidPlan_returnsInvalidPlan() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertProblemDetail(post("/api/v1/" + ctx.businessId() + "/subscription/change-plan",
                ctx.accessToken(), Map.of("plan", "ULTRA")), 400, "INVALID_PLAN");
    }

    @Test
    void changePlan_missingPlan_returnsMissingPlan() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertProblemDetail(post("/api/v1/" + ctx.businessId() + "/subscription/change-plan",
                ctx.accessToken(), Map.of()), 400, "MISSING_PLAN");
    }

    @Test
    void adminPlanChange_tenantDenied_superAdminAllowed() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertProblemDetail(patch("/api/v1/admin/businesses/" + ctx.businessId() + "/plan",
                ctx.accessToken(), Map.of("plan", "PRO")), 403, "ACCESS_DENIED");
        patch("/api/v1/admin/businesses/" + ctx.businessId() + "/plan",
                adminToken(), Map.of("plan", "PRO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan").value("PRO"));
    }

    @Test
    void adminCreatePlan_successAndDuplicate() throws Exception {
        String admin = adminToken();
        String name = "TestPlan" + random().toUpperCase();
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("name", name);
        body.put("description", "created by integration test");
        body.put("price", 1999);
        body.put("currency", "NGN");
        body.put("features", List.of(
                Map.of("featureKey", "max_shops", "value", "5", "isHardLimit", true),
                Map.of("featureKey", "max_users", "value", "10", "isHardLimit", true),
                Map.of("featureKey", "advancedAnalytics", "value", "false", "isHardLimit", false)));

        MvcResult res = post("/api/v1/admin/billing/plans", admin, body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value(name))
                .andExpect(jsonPath("$.versions[0].price").value(1999))
                .andReturn();
        UUID planId = extractUuid(res, "$.id");

        // Duplicate name
        assertProblemDetail(post("/api/v1/admin/billing/plans", admin, body), 400, "PLAN_EXISTS");

        // Add a new priced version (grandfathering — never mutate an existing price)
        post("/api/v1/admin/billing/plans/" + planId + "/versions", admin,
                Map.of("price", 2999, "currency", "NGN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versions.length()").value(2))
                .andExpect(jsonPath("$.versions[1].price").value(2999));
    }

    @Test
    void adminCreatePlan_tenantDenied() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertProblemDetail(post("/api/v1/admin/billing/plans", ctx.accessToken(),
                Map.of("name", "X" + random().toUpperCase(), "price", 1000)), 403, "ACCESS_DENIED");
    }

    // ---------------------------------------------------------------------
    // Shops
    // ---------------------------------------------------------------------

    @Test
    void createShop_freePlanLimitThenUpgradeThenSuccess() throws Exception {
        // During the trial all features are enabled, so the FREE plan hard limit
        // (maxShops=1) only applies once the trial has expired.
        AuthContext ctx = registerBusinessAndOwner();
        expireTrial(ctx.businessId());
        Map<String, Object> shopBody = Map.of(
                "name", "Second Branch",
                "code", "BR" + random().substring(0, 4).toUpperCase());
        assertProblemDetail(post("/api/v1/" + ctx.businessId() + "/shops", ctx.accessToken(), shopBody),
                400, "PLAN_LIMIT_EXCEEDED");

        // After upgrading to STARTER (maxShops=3) the same create succeeds.
        changeToStarter(ctx);
        post("/api/v1/" + ctx.businessId() + "/shops", ctx.accessToken(), shopBody)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Second Branch"));
    }

    @Test
    void createShop_duplicateCode_returnsShopCodeExists() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        changeToStarter(ctx);
        String code = "DUP" + random().substring(0, 4).toUpperCase();
        post("/api/v1/" + ctx.businessId() + "/shops", ctx.accessToken(),
                Map.of("name", "One", "code", code))
                .andExpect(status().isCreated());
        assertProblemDetail(post("/api/v1/" + ctx.businessId() + "/shops", ctx.accessToken(),
                Map.of("name", "Two", "code", code)), 400, "SHOP_CODE_EXISTS");
    }

    @Test
    void createShop_validationErrors() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        changeToStarter(ctx);
        assertValidation(post("/api/v1/" + ctx.businessId() + "/shops", ctx.accessToken(),
                Map.of("name", "", "code", "lower case!")), Map.of(
                "name", "Shop name is required",
                "code", "Shop code must be 2-20 uppercase letters, digits, hyphens, or underscores"));
    }

    @Test
    void listShops_returnsRegisteredShop() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        get("/api/v1/" + ctx.businessId() + "/shops", ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Main Shop"));
    }

    @Test
    void getShop_success_andCrossTenantNotExposed() throws Exception {
        AuthContext a = registerBusinessAndOwner();
        AuthContext b = registerBusinessAndOwner();
        get("/api/v1/" + a.businessId() + "/shops/" + a.shopId(), a.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(a.shopId().toString()));
        // Tenant A must not be able to read tenant B's shop.
        assertProblemDetail(get("/api/v1/" + a.businessId() + "/shops/" + b.shopId(), a.accessToken()),
                400, "SHOP_NOT_FOUND");
    }

    @Test
    void updateShop_renamesShop() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        put("/api/v1/" + ctx.businessId() + "/shops/" + ctx.shopId(), ctx.accessToken(),
                Map.of("name", "Head Office"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Head Office"));
    }

    @Test
    void deactivateAndReactivateShop() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        patch("/api/v1/" + ctx.businessId() + "/shops/" + ctx.shopId() + "/deactivate",
                ctx.accessToken(), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Shop deactivated successfully"));
        get("/api/v1/" + ctx.businessId() + "/shops/" + ctx.shopId(), ctx.accessToken())
                .andExpect(jsonPath("$.active").value(false));
        patch("/api/v1/" + ctx.businessId() + "/shops/" + ctx.shopId() + "/reactivate",
                ctx.accessToken(), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }

    // ---------------------------------------------------------------------
    // Service catalog
    // ---------------------------------------------------------------------

    @Test
    void listServices_returnsSeededDefaults() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        get("/api/v1/" + ctx.businessId() + "/catalog/services", ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6))
                .andExpect(jsonPath("$[0].name").value("Wash & Fold"));
    }

    @Test
    void listGarments_returnsSeededDefaults() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        get("/api/v1/" + ctx.businessId() + "/catalog/garments", ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(12))
                .andExpect(jsonPath("$[0].name").value("Shirt"));
    }

    @Test
    void createService_successDuplicateValidation() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        String name = "Express Service " + random();
        Map<String, Object> body = Map.of(
                "name", name,
                "category", "EXPRESS",
                "defaultPrice", 15.00);
        MvcResult res = post("/api/v1/" + ctx.businessId() + "/catalog/services", ctx.accessToken(), body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(name))
                .andExpect(jsonPath("$.defaultPrice").value(15.00))
                .andReturn();
        UUID serviceId = extractUuid(res, "$.id");

        assertProblemDetail(post("/api/v1/" + ctx.businessId() + "/catalog/services",
                ctx.accessToken(), body), 400, "SERVICE_EXISTS");

        assertValidation(post("/api/v1/" + ctx.businessId() + "/catalog/services",
                ctx.accessToken(), Map.of("name", "")), Map.of(
                "name", "Service name is required",
                "category", "Category is required",
                "defaultPrice", "Default price is required"));
    }

    @Test
    void updateAndDeleteService() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        String name = "To Delete " + random();
        MvcResult res = post("/api/v1/" + ctx.businessId() + "/catalog/services", ctx.accessToken(),
                Map.of("name", name, "category", "SPECIAL", "defaultPrice", 5))
                .andExpect(status().isOk()).andReturn();
        UUID serviceId = extractUuid(res, "$.id");

        put("/api/v1/" + ctx.businessId() + "/catalog/services/" + serviceId, ctx.accessToken(),
                Map.of("description", "updated", "isActive", false))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("updated"))
                .andExpect(jsonPath("$.isActive").value(false));

        delete("/api/v1/" + ctx.businessId() + "/catalog/services/" + serviceId, ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Service deleted"));
    }

    @Test
    void createGarment_successAndDuplicate() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        String name = "Cape " + random();
        post("/api/v1/" + ctx.businessId() + "/catalog/garments", ctx.accessToken(),
                Map.of("name", name, "category", "OUTERWEAR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(name));
        assertProblemDetail(post("/api/v1/" + ctx.businessId() + "/catalog/garments",
                ctx.accessToken(), Map.of("name", name, "category", "OUTERWEAR")), 400, "GARMENT_EXISTS");
    }

    @Test
    void setPricingAndLookup_specificThenDefault() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        UUID serviceId = UUID.fromString(extractUuidString(ctx, "/catalog/services", 0));
        UUID garmentId = UUID.fromString(extractUuidString(ctx, "/catalog/garments", 0));

        // Unpriced combo first → falls back to the service default price.
        get("/api/v1/" + ctx.businessId() + "/catalog/pricing/lookup"
                + "?serviceTypeId=" + serviceId + "&garmentTypeId=" + garmentId, ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("DEFAULT"));

        // Set a specific price, then the lookup must use it.
        post("/api/v1/" + ctx.businessId() + "/catalog/pricing", ctx.accessToken(),
                Map.of("serviceTypeId", serviceId.toString(),
                        "garmentTypeId", garmentId.toString(),
                        "price", 12.50))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(12.50));

        get("/api/v1/" + ctx.businessId() + "/catalog/pricing/lookup"
                + "?serviceTypeId=" + serviceId + "&garmentTypeId=" + garmentId, ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("SPECIFIC"))
                .andExpect(jsonPath("$.price").value(12.50));
    }

    @Test
    void removePricing_softDeletes() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        UUID serviceId = UUID.fromString(extractUuidString(ctx, "/catalog/services", 0));
        UUID garmentId = UUID.fromString(extractUuidString(ctx, "/catalog/garments", 0));
        MvcResult res = post("/api/v1/" + ctx.businessId() + "/catalog/pricing", ctx.accessToken(),
                Map.of("serviceTypeId", serviceId.toString(),
                        "garmentTypeId", garmentId.toString(),
                        "price", 7.00))
                .andExpect(status().isOk()).andReturn();
        UUID pricingId = extractUuid(res, "$.id");

        delete("/api/v1/" + ctx.businessId() + "/catalog/pricing/" + pricingId, ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Pricing removed"));

        // After removal the lookup falls back to the default again.
        get("/api/v1/" + ctx.businessId() + "/catalog/pricing/lookup"
                + "?serviceTypeId=" + serviceId + "&garmentTypeId=" + garmentId, ctx.accessToken())
                .andExpect(jsonPath("$.source").value("DEFAULT"));
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private String extractUuidString(AuthContext ctx, String path, int index) throws Exception {
        MvcResult res = get("/api/v1/" + ctx.businessId() + path, ctx.accessToken())
                .andExpect(status().isOk()).andReturn();
        String json = res.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        return com.jayway.jsonpath.JsonPath.read(json, "$[" + index + "].id").toString();
    }
}
