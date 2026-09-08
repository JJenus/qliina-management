package com.jjenus.qliina_management.integration;

import com.jjenus.qliina_management.business.service.ServiceCatalogService;
import com.jjenus.qliina_management.payment.provider.SimulatorPaymentProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.ResultActions;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Payment-provider SPI suite: catalog + per-business enablement, checkout
 * routing (CARD/TRANSFER via provider), decline handling and webhook signature
 * verification. Real processors are never reached — tests drive the simulator
 * and assert fail-closed behavior for disabled/unknown providers.
 */
class PaymentProviderIntegrationTest extends BaseIntegrationTest {

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

    private String newPhone() {
        return "+1" + (555_800_0000L + counter.incrementAndGet());
    }

    private UUID createCustomer(AuthContext ctx) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("firstName", "Prov");
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
    // Provider catalog
    // ---------------------------------------------------------------------

    @Test
    void listProviders_defaults() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        get(providersBase(ctx.businessId()), ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name=='simulator')].enabled").value(true))
                .andExpect(jsonPath("$[?(@.name=='simulator')].configured").value(true))
                .andExpect(jsonPath("$[?(@.name=='simulator')].available").value(true))
                .andExpect(jsonPath("$[?(@.name=='paystack')].enabled").value(false))
                .andExpect(jsonPath("$[?(@.name=='paystack')].configured").value(false))
                .andExpect(jsonPath("$[?(@.name=='flutterwave')].enabled").value(false))
                .andExpect(jsonPath("$[?(@.name=='flutterwave')].configured").value(false));
    }

    @Test
    void listProviders_requiresAuth() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        get(providersBase(ctx.businessId()), null).andExpect(status().isUnauthorized());
    }

    @Test
    void setProviderEnabled_realProviderTogglePersists() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        patch(providersBase(ctx.businessId()) + "/flutterwave/enabled?enabled=true", ctx.accessToken(), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("flutterwave"))
                .andExpect(jsonPath("$.enabled").value(true))
                // No secrets in test env — enabled but NOT configured/available.
                .andExpect(jsonPath("$.configured").value(false))
                .andExpect(jsonPath("$.available").value(false));

        get(providersBase(ctx.businessId()), ctx.accessToken())
                .andExpect(jsonPath("$[?(@.name=='flutterwave')].enabled").value(true));
    }

    @Test
    void setProviderEnabled_unknownProvider() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertProblemDetail(patch(providersBase(ctx.businessId()) + "/nope/enabled?enabled=true",
                ctx.accessToken(), null), 400, "PROVIDER_UNKNOWN");
    }

    // ---------------------------------------------------------------------
    // Checkout routing
    // ---------------------------------------------------------------------

    @Test
    void processPayment_cardWithoutProvider() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        UUID orderId = newOrder(ctx);
        assertProblemDetail(post(payBase(ctx.businessId()) + "/orders/" + orderId + "/process",
                ctx.accessToken(), Map.of("amount", 7.0, "method", "CARD")),
                400, "PAYMENT_PROVIDER_REQUIRED");
    }

    @Test
    void processPayment_cardUnknownProvider() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        UUID orderId = newOrder(ctx);
        assertProblemDetail(post(payBase(ctx.businessId()) + "/orders/" + orderId + "/process",
                ctx.accessToken(), Map.of("amount", 7.0, "method", "CARD", "provider", "nope")),
                400, "PROVIDER_UNKNOWN");
    }

    @Test
    void processPayment_cardDisabledProvider() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        // Simulator is on by default; disable it to exercise the fail-closed gate.
        patch(providersBase(ctx.businessId()) + "/simulator/enabled?enabled=false", ctx.accessToken(), null)
                .andExpect(status().isOk());

        UUID orderId = newOrder(ctx);
        assertProblemDetail(post(payBase(ctx.businessId()) + "/orders/" + orderId + "/process",
                ctx.accessToken(), Map.of("amount", 7.0, "method", "CARD", "provider", "simulator")),
                400, "PROVIDER_DISABLED");
    }

    @Test
    void processPayment_cardViaSimulatorSettles() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        UUID orderId = newOrder(ctx);

        String json = post(payBase(ctx.businessId()) + "/orders/" + orderId + "/process",
                ctx.accessToken(), Map.of("amount", 7.0, "method", "CARD", "provider", "simulator"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.isFullyPaid").value(true))
                .andExpect(jsonPath("$.provider").value("simulator"))
                .andExpect(jsonPath("$.providerReference").exists())
                .andReturn().getResponse().getContentAsString();
        String providerReference = readString(json, "$.providerReference");

        // Payment details expose the provider fields.
        UUID paymentId = readUuid(json, "$.paymentId");
        get(payBase(ctx.businessId()) + "/" + paymentId, ctx.accessToken())
                .andExpect(jsonPath("$.provider").value("simulator"))
                .andExpect(jsonPath("$.providerReference").value(providerReference));
    }

    @Test
    void processPayment_cardDeclined() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        UUID orderId = newOrder(ctx);

        simulatorPaymentProvider.forceFailure(true);
        try {
            post(payBase(ctx.businessId()) + "/orders/" + orderId + "/process",
                    ctx.accessToken(), Map.of("amount", 7.0, "method", "CARD", "provider", "simulator"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.status").value("FAILED"))
                    .andExpect(jsonPath("$.message").exists())
                    .andExpect(jsonPath("$.providerReference").exists());

            // A declined attempt records no payment and leaves the order unpaid.
            get(payBase(ctx.businessId()), ctx.accessToken())
                    .andExpect(jsonPath("$.totalElements").value(0));
        } finally {
            simulatorPaymentProvider.forceFailure(false);
        }
    }

    // ---------------------------------------------------------------------
    // Verify
    // ---------------------------------------------------------------------

    @Test
    void verify_settledCardPayment() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        UUID orderId = newOrder(ctx);
        String json = post(payBase(ctx.businessId()) + "/orders/" + orderId + "/process",
                ctx.accessToken(), Map.of("amount", 7.0, "method", "CARD", "provider", "simulator"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        UUID paymentId = readUuid(json, "$.paymentId");

        post(providersBase(ctx.businessId()) + "/" + paymentId + "/verify", ctx.accessToken(), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paid").value(true))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.provider").value("simulator"));
    }

    @Test
    void verify_cashPaymentNotProviderBacked() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        UUID orderId = newOrder(ctx);
        String json = post(payBase(ctx.businessId()) + "/orders/" + orderId + "/process",
                ctx.accessToken(), Map.of("amount", 7.0, "method", "CASH"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        UUID paymentId = readUuid(json, "$.paymentId");

        assertProblemDetail(post(providersBase(ctx.businessId()) + "/" + paymentId + "/verify",
                ctx.accessToken(), null), 400, "NOT_PROVIDER_PAYMENT");
    }

    // ---------------------------------------------------------------------
    // Webhooks
    // ---------------------------------------------------------------------

    @Test
    void processPayment_cardRedirectProvider_pendingNotCounted() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        UUID orderId = newOrder(ctx);

        simulatorPaymentProvider.forceRedirect(true);
        try {
            // Redirect providers (approved=false + checkoutUrl) persist PENDING and do
            // NOT count toward the paid balance until the webhook/verify settles them.
            post(payBase(ctx.businessId()) + "/orders/" + orderId + "/process",
                    ctx.accessToken(), Map.of("amount", 7.0, "method", "CARD", "provider", "simulator"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.status").value("PENDING"))
                    .andExpect(jsonPath("$.checkoutUrl").exists())
                    .andExpect(jsonPath("$.balanceDue").value(7.0))
                    .andExpect(jsonPath("$.isFullyPaid").value(false))
                    .andExpect(jsonPath("$.provider").value("simulator"))
                    .andExpect(jsonPath("$.providerReference").exists());
        } finally {
            simulatorPaymentProvider.forceRedirect(false);
        }
    }

    @Test
    void verify_settlesPendingRedirectPayment() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        UUID orderId = newOrder(ctx);

        simulatorPaymentProvider.forceRedirect(true);
        String json;
        try {
            json = post(payBase(ctx.businessId()) + "/orders/" + orderId + "/process",
                    ctx.accessToken(), Map.of("amount", 7.0, "method", "TRANSFER", "provider", "simulator"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("PENDING"))
                    .andReturn().getResponse().getContentAsString();
        } finally {
            simulatorPaymentProvider.forceRedirect(false);
        }
        UUID paymentId = readUuid(json, "$.paymentId");

        post(providersBase(ctx.businessId()) + "/" + paymentId + "/verify", ctx.accessToken(), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paid").value(true))
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        // Settled funds now count — payment flips to COMPLETED, order shows paid.
        get(payBase(ctx.businessId()) + "/" + paymentId, ctx.accessToken())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.provider").value("simulator"));
    }

    @Test
    void webhook_settlesPendingRedirectPayment() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        UUID orderId = newOrder(ctx);

        simulatorPaymentProvider.forceRedirect(true);
        String json;
        try {
            json = post(payBase(ctx.businessId()) + "/orders/" + orderId + "/process",
                    ctx.accessToken(), Map.of("amount", 7.0, "method", "CARD", "provider", "simulator"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("PENDING"))
                    .andReturn().getResponse().getContentAsString();
        } finally {
            simulatorPaymentProvider.forceRedirect(false);
        }
        String providerReference = readString(json, "$.providerReference");

        mockMvc.perform(MockMvcRequestBuilders
                .post("/api/v1/webhooks/payments/simulator")
                .contentType(MediaType.APPLICATION_JSON)
                .header("x-sim-secret", "sim-secret")
                .content("{\"txn\":\"" + providerReference + "\",\"event\":\"charge.succeeded\","
                        + "\"amount\":7.0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("received"));

        UUID paymentId = readUuid(json, "$.paymentId");
        get(payBase(ctx.businessId()) + "/" + paymentId, ctx.accessToken())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void webhook_badSignatureRejected() throws Exception {
        ResultActions res = mockMvc.perform(MockMvcRequestBuilders
                .post("/api/v1/webhooks/payments/simulator")
                .contentType(MediaType.APPLICATION_JSON)
                .header("x-sim-secret", "wrong-secret")
                .content("{\"txn\":\"sim_x\",\"event\":\"charge.succeeded\",\"amount\":7.0}"));
        assertProblemDetail(res, 400, "INVALID_WEBHOOK_SIGNATURE");
    }

    @Test
    void webhook_settledChargeReconciles() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        UUID orderId = newOrder(ctx);
        String json = post(payBase(ctx.businessId()) + "/orders/" + orderId + "/process",
                ctx.accessToken(), Map.of("amount", 7.0, "method", "CARD", "provider", "simulator"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String providerReference = readString(json, "$.providerReference");

        mockMvc.perform(MockMvcRequestBuilders
                .post("/api/v1/webhooks/payments/simulator")
                .contentType(MediaType.APPLICATION_JSON)
                .header("x-sim-secret", "sim-secret")
                .content("{\"txn\":\"" + providerReference + "\",\"event\":\"charge.succeeded\","
                        + "\"amount\":7.0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("received"));

        // The reconciled payment is (still) completed.
        UUID paymentId = readUuid(json, "$.paymentId");
        get(payBase(ctx.businessId()) + "/" + paymentId, ctx.accessToken())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }
}