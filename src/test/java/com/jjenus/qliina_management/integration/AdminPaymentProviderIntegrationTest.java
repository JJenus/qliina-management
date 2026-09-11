package com.jjenus.qliina_management.integration;

import com.jjenus.qliina_management.business.service.ServiceCatalogService;
import com.jjenus.qliina_management.payment.provider.SimulatorPaymentProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Platform-wide payment-provider connectivity control. The platform admin flips a
 * provider off for every business at once — it vanishes from business gateway
 * screens and every connect/checkout attempt fails closed, while authorizations that
 * were already in flight keep verifying. All assertions drive the simulator.
 */
class AdminPaymentProviderIntegrationTest extends BaseIntegrationTest {

    private static final String ADMIN_PROVIDERS = "/api/v1/admin/payment-providers";

    @Autowired
    private ServiceCatalogService catalogService;

    @Autowired
    private SimulatorPaymentProvider simulatorPaymentProvider;

    private String providersBase(UUID businessId) {
        return "/api/v1/" + businessId + "/payment-providers";
    }

    private String payBase(UUID businessId) {
        return "/api/v1/" + businessId + "/payments";
    }

    /** Platform availability is process-global — always restore it after a test. */
    @AfterEach
    void restoreSimulatorAvailability() throws Exception {
        patch(ADMIN_PROVIDERS + "/simulator/availability?platformEnabled=true", adminToken(), null);
    }

    private String newPhone() {
        return "+1" + (556_000_0000L + counter.incrementAndGet());
    }

    private UUID createCustomer(AuthContext ctx) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("firstName", "Plat");
        body.put("lastName", "Customer");
        body.put("phone", newPhone());
        String json = post("/api/v1/" + ctx.businessId() + "/customers", ctx.accessToken(), body)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return readUuid(json, "$.id");
    }

    /** Creates an order (total 7.0) and returns its id. */
    private UUID newOrder(AuthContext ctx) throws Exception {
        UUID custId = createCustomer(ctx);
        UUID serviceTypeId = catalogService.getActiveServices(ctx.businessId()).get(0).getId();
        UUID garmentTypeId = catalogService.getActiveGarments(ctx.businessId()).get(0).getId();
        Map<String, Object> body = new HashMap<>();
        body.put("customerId", custId.toString());
        body.put("shopId", ctx.shopId().toString());
        body.put("items", List.of(Map.of(
                "serviceTypeId", serviceTypeId.toString(),
                "garmentTypeId", garmentTypeId.toString(),
                "quantity", 2,
                "unitPrice", 3.50,
                "description", "Two shirts")));
        ResultActions rs = post("/api/v1/" + ctx.businessId() + "/orders", ctx.accessToken(), body);
        rs.andExpect(status().isOk());
        return extractUuid(rs.andReturn(), "$.id");
    }

    // ---------------------------------------------------------------------
    // Platform catalog
    // ---------------------------------------------------------------------

    @Test
    void admin_listProviders_showsPlatformCatalog() throws Exception {
        get(ADMIN_PROVIDERS, adminToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[?(@.name=='simulator')].displayName").value("Simulator (sandbox)"))
                .andExpect(jsonPath("$[?(@.name=='simulator')].configured").value(true))
                .andExpect(jsonPath("$[?(@.name=='simulator')].platformEnabled").value(true))
                .andExpect(jsonPath("$[?(@.name=='simulator')].supportsPlatformSubaccounts").value(true))
                .andExpect(jsonPath("$[?(@.name=='paystack')].configured").value(false))
                .andExpect(jsonPath("$[?(@.name=='paystack')].platformEnabled").value(true))
                .andExpect(jsonPath("$[?(@.name=='flutterwave')].platformEnabled").value(true))
                // Counts are numbers; exact values depend on sibling tests' contexts.
                .andExpect(jsonPath("$[?(@.name=='simulator')].businessesConnected")
                        .value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.isA(Number.class))));
    }

    @Test
    void admin_listProviders_requiresPlatformAuth() throws Exception {
        get(ADMIN_PROVIDERS, null).andExpect(status().isUnauthorized());

        AuthContext ctx = registerBusinessAndOwner();
        get(ADMIN_PROVIDERS, ctx.accessToken()).andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------------
    // Availability toggle
    // ---------------------------------------------------------------------

    @Test
    void admin_toggle_disablesAndReEnables() throws Exception {
        String token = adminToken();

        patch(ADMIN_PROVIDERS + "/simulator/availability?platformEnabled=false", token, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("simulator"))
                .andExpect(jsonPath("$.platformEnabled").value(false));
        get(ADMIN_PROVIDERS, token)
                .andExpect(jsonPath("$[?(@.name=='simulator')].platformEnabled").value(false));

        patch(ADMIN_PROVIDERS + "/simulator/availability?platformEnabled=true", token, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.platformEnabled").value(true));
        get(ADMIN_PROVIDERS, token)
                .andExpect(jsonPath("$[?(@.name=='simulator')].platformEnabled").value(true));
    }

    @Test
    void admin_toggle_unknownProvider() throws Exception {
        assertProblemDetail(patch(ADMIN_PROVIDERS + "/nope/availability?platformEnabled=false",
                adminToken(), null), 400, "PROVIDER_UNKNOWN");
    }

    // ---------------------------------------------------------------------
    // Business-side effect of a platform disable
    // ---------------------------------------------------------------------

    @Test
    void admin_disable_hidesProviderFromBusiness_andFailsClosed() throws Exception {
        String admin = adminToken();
        patch(ADMIN_PROVIDERS + "/simulator/availability?platformEnabled=false", admin, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.platformEnabled").value(false));

        // The disabled provider vanishes from the business gateway catalog…
        AuthContext ctx = registerBusinessAndOwner();
        get(providersBase(ctx.businessId()), ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name=='simulator')]").isEmpty())
                .andExpect(jsonPath("$[?(@.name=='paystack')]").exists())
                .andExpect(jsonPath("$[?(@.name=='flutterwave')]").exists());

        // …a business-level connect attempt is rejected (fail closed)…
        assertProblemDetail(patch(providersBase(ctx.businessId()) + "/simulator/enabled?enabled=true",
                ctx.accessToken(), null), 400, "PROVIDER_UNAVAILABLE");
        assertProblemDetail(put(providersBase(ctx.businessId()) + "/simulator/connection",
                ctx.accessToken(), Map.of("mode", "PLATFORM")), 400, "PROVIDER_UNAVAILABLE");

        // …and a checkout that names it fails closed rather than hitting the gateway.
        UUID orderId = newOrder(ctx);
        assertProblemDetail(post(payBase(ctx.businessId()) + "/orders/" + orderId + "/process",
                ctx.accessToken(), Map.of("amount", 7.0, "method", "CARD", "provider", "simulator")),
                400, "PROVIDER_DISABLED");

        // Re-enable on the platform restores the provider for business admins.
        patch(ADMIN_PROVIDERS + "/simulator/availability?platformEnabled=true", admin, null)
                .andExpect(jsonPath("$.platformEnabled").value(true));
        get(providersBase(ctx.businessId()), ctx.accessToken())
                .andExpect(jsonPath("$[?(@.name=='simulator')].available").value(true));
    }

    @Test
    void admin_disable_doesNotStrandInflightAuthorization() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        UUID orderId = newOrder(ctx);

        // Start a redirect (PENDING) authorization while the provider is available.
        simulatorPaymentProvider.forceRedirect(true);
        String json;
        try {
            json = post(payBase(ctx.businessId()) + "/orders/" + orderId + "/generate",
                    ctx.accessToken(), Map.of("provider", "simulator"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("PENDING"))
                    .andReturn().getResponse().getContentAsString();
        } finally {
            simulatorPaymentProvider.forceRedirect(false);
        }
        UUID paymentId = readUuid(json, "$.paymentId");

        // Platform disables the provider mid-flight.
        patch(ADMIN_PROVIDERS + "/simulator/availability?platformEnabled=false", adminToken(), null)
                .andExpect(jsonPath("$.platformEnabled").value(false));

        // The pending authorization still verifies and settles — the toggle never
        // strands money that was already being collected.
        post(providersBase(ctx.businessId()) + "/" + paymentId + "/verify", ctx.accessToken(), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paid").value(true))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
        get(payBase(ctx.businessId()) + "?orderId=" + orderId + "&status=COMPLETED", ctx.accessToken())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void admin_togglePersistsBusinessConnections() throws Exception {
        // A business connects while the provider is available…
        AuthContext ctx = registerBusinessAndOwner();
        put(providersBase(ctx.businessId()) + "/simulator/connection", ctx.accessToken(),
                Map.of("mode", "PLATFORM"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true));

        // …the platform disables and re-enables it…
        String admin = adminToken();
        patch(ADMIN_PROVIDERS + "/simulator/availability?platformEnabled=false", admin, null);
        patch(ADMIN_PROVIDERS + "/simulator/availability?platformEnabled=true", admin, null)
                .andExpect(jsonPath("$.platformEnabled").value(true));

        // …and the business's stored connection is intact (no silent downgrade).
        get(providersBase(ctx.businessId()), ctx.accessToken())
                .andExpect(jsonPath("$[?(@.name=='simulator')].connectionMode").value("PLATFORM"))
                .andExpect(jsonPath("$[?(@.name=='simulator')].available").value(true));
    }
}