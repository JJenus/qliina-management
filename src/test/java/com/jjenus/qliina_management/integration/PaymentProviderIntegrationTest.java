package com.jjenus.qliina_management.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjenus.qliina_management.business.service.ServiceCatalogService;
import com.jjenus.qliina_management.payment.provider.SimulatorPaymentProvider;
import com.jjenus.qliina_management.payment.service.PaymentProviderService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.hamcrest.Matchers.contains;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.ResultActions;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import java.time.LocalDateTime;

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

    @Autowired
    private PaymentProviderService paymentProviderService;

    @Autowired
    private ObjectMapper mapper;

    /** Finds a queue item's id by provider reference from a raw content-array JSON document. */
    private UUID findItemId(String contentJson, String providerReference) throws Exception {
        JsonNode content = mapper.readTree(contentJson).path("content");
        for (JsonNode node : content) {
            if (providerReference.equals(node.path("providerReference").asText())) {
                return UUID.fromString(node.path("id").asText());
            }
        }
        return null;
    }

    private String providersBase(UUID businessId) {
        return "/api/v1/" + businessId + "/payment-providers";
    }

    private String payBase(UUID businessId) {
        return "/api/v1/" + businessId + "/payments";
    }

    private String reconcileBase(UUID businessId) {
        return "/api/v1/" + businessId + "/payment-reconciliation";
    }

    private ResultActions simWebhook(String txn, String event, double amount) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders
                .post("/api/v1/webhooks/payments/simulator")
                .contentType(MediaType.APPLICATION_JSON)
                .header("x-sim-secret", "sim-secret")
                .content("{\"txn\":\"" + txn + "\",\"event\":\"charge." + event + "\",\"amount\":" + amount + "}"));
    }

    /** Creates a redirect (PENDING) provider payment on a fresh 7.0 order. */
    private String createPendingRedirect(AuthContext ctx, UUID orderId) throws Exception {
        simulatorPaymentProvider.forceRedirect(true);
        try {
            return post(payBase(ctx.businessId()) + "/orders/" + orderId + "/process",
                    ctx.accessToken(), Map.of("amount", 7.0, "method", "CARD", "provider", "simulator"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("PENDING"))
                    .andReturn().getResponse().getContentAsString();
        } finally {
            simulatorPaymentProvider.forceRedirect(false);
        }
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
                // The simulator is platform-connected by default in dev/test; it
                // carries no business-owned credentials of its own.
                .andExpect(jsonPath("$[?(@.name=='simulator')].connectionMode").value("PLATFORM"))
                .andExpect(jsonPath("$[?(@.name=='simulator')].hasCredentials").value(false))
                .andExpect(jsonPath("$[?(@.name=='simulator')].supportsPlatformSubaccounts").value(true))
                .andExpect(jsonPath("$[?(@.name=='paystack')].enabled").value(false))
                .andExpect(jsonPath("$[?(@.name=='paystack')].configured").value(false))
                .andExpect(jsonPath("$[?(@.name=='paystack')].connectionMode").value("DISCONNECTED"))
                .andExpect(jsonPath("$[?(@.name=='paystack')].supportsPlatformSubaccounts").value(true))
                .andExpect(jsonPath("$[?(@.name=='flutterwave')].enabled").value(false))
                .andExpect(jsonPath("$[?(@.name=='flutterwave')].configured").value(false))
                .andExpect(jsonPath("$[?(@.name=='flutterwave')].connectionMode").value("DISCONNECTED"))
                .andExpect(jsonPath("$[?(@.name=='flutterwave')].supportsPlatformSubaccounts").value(false));
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
    // Per-business connection model (Disconnected / Platform / BYO)
    // ---------------------------------------------------------------------

    @Test
    void setProviderConnection_byo_encryptsAndCharges() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        String base = providersBase(ctx.businessId()) + "/simulator/connection";

        String body = put(base, ctx.accessToken(),
                Map.of("mode", "BYO", "secretKey", "sk_test_business_123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("simulator"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.connectionMode").value("BYO"))
                .andExpect(jsonPath("$.hasCredentials").value(true))
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.platformSubaccountId").value(org.hamcrest.Matchers.nullValue()))
                .andReturn().getResponse().getContentAsString();
        assertFalse(body.contains("sk_test_business_123"), "credentials must never be echoed");

        UUID orderId = newOrder(ctx);
        post(payBase(ctx.businessId()) + "/orders/" + orderId + "/process",
                ctx.accessToken(), Map.of("amount", 15.0, "method", "CARD", "provider", "simulator"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.provider").value("simulator"));
    }

    @Test
    void setProviderConnection_byoWithoutSecretKey_rejected() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertProblemDetail(put(providersBase(ctx.businessId()) + "/simulator/connection",
                ctx.accessToken(), Map.of("mode", "BYO")), 400, "PROVIDER_CONFIG_INVALID");
    }

    @Test
    void setProviderConnection_platformWithSubaccount_charges() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        put(providersBase(ctx.businessId()) + "/simulator/connection", ctx.accessToken(),
                Map.of("mode", "PLATFORM", "platformSubaccountId", "SUB_ACCT_001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connectionMode").value("PLATFORM"))
                .andExpect(jsonPath("$.platformSubaccountId").value("SUB_ACCT_001"))
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.hasCredentials").value(false));

        UUID orderId = newOrder(ctx);
        post(payBase(ctx.businessId()) + "/orders/" + orderId + "/process",
                ctx.accessToken(), Map.of("amount", 20.0, "method", "CARD", "provider", "simulator"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void setProviderConnection_disconnected_clearsAndDisables() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        String base = providersBase(ctx.businessId()) + "/simulator/connection";

        put(base, ctx.accessToken(), Map.of("mode", "BYO", "secretKey", "sk_test_business_123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true));

        put(base, ctx.accessToken(), Map.of("mode", "DISCONNECTED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.connectionMode").value("DISCONNECTED"))
                .andExpect(jsonPath("$.hasCredentials").value(false))
                .andExpect(jsonPath("$.available").value(false));

        UUID orderId = newOrder(ctx);
        assertProblemDetail(post(payBase(ctx.businessId()) + "/orders/" + orderId + "/process",
                ctx.accessToken(), Map.of("amount", 9.0, "method", "CARD", "provider", "simulator")),
                400, "PROVIDER_DISABLED");
    }

    @Test
    void setProviderConnection_platformUnconfigured_failsClosed() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        // Flutterwave has no platform keys in the test env: PLATFORM mode persists
        // but the provider stays unavailable and every charge fails closed.
        put(providersBase(ctx.businessId()) + "/flutterwave/connection", ctx.accessToken(),
                Map.of("mode", "PLATFORM"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.connectionMode").value("PLATFORM"))
                .andExpect(jsonPath("$.configured").value(false))
                .andExpect(jsonPath("$.available").value(false));

        UUID orderId = newOrder(ctx);
        assertProblemDetail(post(payBase(ctx.businessId()) + "/orders/" + orderId + "/process",
                ctx.accessToken(), Map.of("amount", 9.0, "method", "CARD", "provider", "flutterwave")),
                400, "PROVIDER_NOT_CONFIGURED");
    }

    @Test
    void setProviderConnection_subaccountOnUnsupportedProvider_rejected() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertProblemDetail(put(providersBase(ctx.businessId()) + "/flutterwave/connection",
                ctx.accessToken(), Map.of("mode", "PLATFORM", "platformSubaccountId", "SUB_004")),
                400, "PROVIDER_CONFIG_INVALID");
    }

    @Test
    void setProviderConnection_unknownProvider() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertProblemDetail(put(providersBase(ctx.businessId()) + "/nope/connection",
                ctx.accessToken(), Map.of("mode", "DISCONNECTED")), 400, "PROVIDER_UNKNOWN");
    }

    // ---------------------------------------------------------------------
    // Checkout routing
    // ---------------------------------------------------------------------

    @Test
    void processPayment_cardWithoutProvider_manuallyRecorded() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        UUID orderId = newOrder(ctx);

        // Brand-new business with no provider config: CARD/TRANSFER are manually
        // recorded (external POS / direct bank transfer) and settle immediately.
        String json = post(payBase(ctx.businessId()) + "/orders/" + orderId + "/process",
                ctx.accessToken(), Map.of("amount", 7.0, "method", "CARD", "reference", "EXT-POS-4242"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.isFullyPaid").value(true))
                .andExpect(jsonPath("$.balanceDue").value(0.0))
                .andExpect(jsonPath("$.provider").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.transactionId").value("EXT-POS-4242"))
                .andReturn().getResponse().getContentAsString();
        UUID paymentId = readUuid(json, "$.paymentId");

        // Recorded COMPLETED with the staff reference; no provider identity.
        get(payBase(ctx.businessId()) + "/" + paymentId, ctx.accessToken())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.method").value("CARD"))
                .andExpect(jsonPath("$.reference").value("EXT-POS-4242"))
                .andExpect(jsonPath("$.provider").value(org.hamcrest.Matchers.nullValue()));

        // Not provider-backed: the verify endpoint refuses it.
        assertProblemDetail(post(providersBase(ctx.businessId()) + "/" + paymentId + "/verify",
                ctx.accessToken(), null), 400, "NOT_PROVIDER_PAYMENT");
    }

    @Test
    void processPayment_transferWithoutProvider_manuallyRecorded() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        UUID orderId = newOrder(ctx);

        post(payBase(ctx.businessId()) + "/orders/" + orderId + "/process",
                ctx.accessToken(), Map.of("amount", 7.0, "method", "TRANSFER", "reference", "TRF-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.isFullyPaid").value(true))
                .andExpect(jsonPath("$.transactionId").value("TRF-123"));
    }

    @Test
    void processPayment_manualRecordWithoutReference() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        UUID orderId = newOrder(ctx);

        // No reference supplied: a manual record still needs a stable transaction id.
        String json = post(payBase(ctx.businessId()) + "/orders/" + orderId + "/process",
                ctx.accessToken(), Map.of("amount", 7.0, "method", "TRANSFER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.transactionId").exists())
                .andReturn().getResponse().getContentAsString();
        assertNotNull(readString(json, "$.transactionId"));
        assertFalse(readString(json, "$.transactionId").isBlank());
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
    void processPayment_cardEnabledButUnconfigured() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        // Enabled for the business but no platform secrets in test env → fail closed.
        patch(providersBase(ctx.businessId()) + "/flutterwave/enabled?enabled=true", ctx.accessToken(), null)
                .andExpect(status().isOk());

        UUID orderId = newOrder(ctx);
        assertProblemDetail(post(payBase(ctx.businessId()) + "/orders/" + orderId + "/process",
                ctx.accessToken(), Map.of("amount", 7.0, "method", "CARD", "provider", "flutterwave")),
                400, "PROVIDER_NOT_CONFIGURED");
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

    @Test
    void splitPayment_declinedCardLegNotCountedAsPaid() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        UUID orderId = newOrder(ctx);

        simulatorPaymentProvider.forceFailure(true);
        try {
            post(payBase(ctx.businessId()) + "/orders/" + orderId + "/split",
                    ctx.accessToken(), Map.of("payments", List.of(
                            Map.of("amount", 3.0, "method", "CASH"),
                            Map.of("amount", 4.0, "method", "CARD", "provider", "simulator"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.status").value("PARTIAL"))
                    // Only the settled (completed) leg counts toward paid.
                    .andExpect(jsonPath("$.amount").value(3.0))
                    .andExpect(jsonPath("$.isFullyPaid").value(false))
                    .andExpect(jsonPath("$.balanceDue").value(4.0))
                    .andExpect(jsonPath("$.errors").isArray());

            // The declined card leg persisted no payment row.
            get(payBase(ctx.businessId()), ctx.accessToken())
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].method").value("CASH"))
                    .andExpect(jsonPath("$.content[0].amount").value(3.0));
        } finally {
            simulatorPaymentProvider.forceFailure(false);
        }
    }

    @Test
    void splitPayment_manualCardLegWithoutProvider() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        UUID orderId = newOrder(ctx);

        // No provider configured: a card split leg is manually recorded like cash.
        post(payBase(ctx.businessId()) + "/orders/" + orderId + "/split",
                ctx.accessToken(), Map.of("payments", List.of(
                        Map.of("amount", 3.0, "method", "CASH"),
                        Map.of("amount", 4.0, "method", "CARD", "reference", "EXT-4242"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.amount").value(7.0))
                .andExpect(jsonPath("$.isFullyPaid").value(true))
                .andExpect(jsonPath("$.balanceDue").value(0.0));

        get(payBase(ctx.businessId()), ctx.accessToken())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[?(@.method=='CARD')].status").value("COMPLETED"));
    }

    @Test
    void splitPayment_redirectPendingLegNotCountedAsPaid() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        UUID orderId = newOrder(ctx);

        simulatorPaymentProvider.forceRedirect(true);
        try {
            post(payBase(ctx.businessId()) + "/orders/" + orderId + "/split",
                    ctx.accessToken(), Map.of("payments", List.of(
                            Map.of("amount", 3.0, "method", "CASH"),
                            Map.of("amount", 4.0, "method", "CARD", "provider", "simulator"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.status").value("PENDING"))
                    // Pending authorizations are not settled funds yet.
                    .andExpect(jsonPath("$.amount").value(3.0))
                    .andExpect(jsonPath("$.isFullyPaid").value(false))
                    .andExpect(jsonPath("$.balanceDue").value(4.0));
        } finally {
            simulatorPaymentProvider.forceRedirect(false);
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

    // ---------------------------------------------------------------------
    // Generate payment / checkout link (Phase 2)
    // ---------------------------------------------------------------------

    @Test
    void generatePayment_createsPendingCheckoutLink() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        UUID orderId = newOrder(ctx);

        String json = post(payBase(ctx.businessId()) + "/orders/" + orderId + "/generate",
                ctx.accessToken(), Map.of("provider", "simulator"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.provider").value("simulator"))
                .andExpect(jsonPath("$.checkoutUrl").exists())
                .andExpect(jsonPath("$.providerReference").exists())
                .andExpect(jsonPath("$.amount").value(7.0))
                .andExpect(jsonPath("$.balanceDue").value(7.0))
                .andExpect(jsonPath("$.isFullyPaid").value(false))
                .andReturn().getResponse().getContentAsString();

        // The QR payload encodes the customer checkout URL.
        String checkoutUrl = readString(json, "$.checkoutUrl");
        assertNotNull(checkoutUrl);
        assertEquals(checkoutUrl, readString(json, "$.qrPayload"));

        UUID paymentId = readUuid(json, "$.paymentId");
        get(payBase(ctx.businessId()) + "/" + paymentId, ctx.accessToken())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.providerReference").exists());
    }

    @Test
    void generatePayment_customAmountAndMethod() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        UUID orderId = newOrder(ctx);

        post(payBase(ctx.businessId()) + "/orders/" + orderId + "/generate",
                ctx.accessToken(), Map.of("provider", "simulator", "amount", 4.0, "method", "TRANSFER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.amount").value(4.0))
                .andExpect(jsonPath("$.method").value("TRANSFER"))
                .andExpect(jsonPath("$.balanceDue").value(7.0));
    }

    @Test
    void generatePayment_unavailableProvider_failsClosed() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        UUID orderId = newOrder(ctx);
        // A known, enabled-but-unprovisioned provider (no secrets) must never
        // start a checkout.
        patch(providersBase(ctx.businessId()) + "/flutterwave/enabled?enabled=true", ctx.accessToken(), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(false));
        assertProblemDetail(post(payBase(ctx.businessId()) + "/orders/" + orderId + "/generate",
                ctx.accessToken(), Map.of("provider", "flutterwave")), 400, "PROVIDER_NOT_CONFIGURED");
    }

    @Test
    void generatePayment_unknownProvider() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        UUID orderId = newOrder(ctx);
        assertProblemDetail(post(payBase(ctx.businessId()) + "/orders/" + orderId + "/generate",
                ctx.accessToken(), Map.of("provider", "nope")), 400, "PROVIDER_UNKNOWN");
    }

    @Test
    void generatePayment_orderAlreadyPaid_rejected() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        UUID orderId = newOrder(ctx);
        post(payBase(ctx.businessId()) + "/orders/" + orderId + "/process",
                ctx.accessToken(), Map.of("amount", 7.0, "method", "CASH"))
                .andExpect(jsonPath("$.isFullyPaid").value(true));

        assertProblemDetail(post(payBase(ctx.businessId()) + "/orders/" + orderId + "/generate",
                ctx.accessToken(), Map.of("provider", "simulator")), 400, "ORDER_ALREADY_PAID");
    }

    @Test
    void generatePayment_amountExceedsBalance_rejected() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        UUID orderId = newOrder(ctx);
        assertProblemDetail(post(payBase(ctx.businessId()) + "/orders/" + orderId + "/generate",
                ctx.accessToken(), Map.of("provider", "simulator", "amount", 50.0)), 400, "PAYMENT_AMOUNT_EXCEEDS_DUE");
    }

    @Test
    void listPayments_pendingFilterCapturesGeneratedLink() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        UUID orderId = newOrder(ctx);
        post(payBase(ctx.businessId()) + "/orders/" + orderId + "/generate",
                ctx.accessToken(), Map.of("provider", "simulator"))
                .andExpect(status().isOk());

        get(payBase(ctx.businessId()) + "?orderId=" + orderId + "&status=PENDING", ctx.accessToken())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].status").value("PENDING"))
                .andExpect(jsonPath("$.content[0].provider").value("simulator"));

        get(payBase(ctx.businessId()) + "?orderId=" + orderId + "&status=COMPLETED", ctx.accessToken())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    // ---------------------------------------------------------------------
    // Webhook auto-link hardening (Phase 2)
    // ---------------------------------------------------------------------

    @Test
    void webhook_amountMismatch_doesNotSettle() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        UUID orderId = newOrder(ctx);
        String json = createPendingRedirect(ctx, orderId);
        UUID paymentId = readUuid(json, "$.paymentId");
        String providerReference = readString(json, "$.providerReference");

        // Signed webhook but for a wildly different amount — must NOT settle.
        simWebhook(providerReference, "succeeded", 999.0)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("received"));

        get(payBase(ctx.businessId()) + "/" + paymentId, ctx.accessToken())
                .andExpect(jsonPath("$.status").value("PENDING"));
        get(payBase(ctx.businessId()) + "?orderId=" + orderId + "&status=COMPLETED", ctx.accessToken())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void webhook_chargeFailed_transitionsToFailed() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        UUID orderId = newOrder(ctx);
        String json = createPendingRedirect(ctx, orderId);
        UUID paymentId = readUuid(json, "$.paymentId");
        String providerReference = readString(json, "$.providerReference");

        simWebhook(providerReference, "failed", 7.0)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("received"));

        get(payBase(ctx.businessId()) + "/" + paymentId, ctx.accessToken())
                .andExpect(jsonPath("$.status").value("FAILED"));
        // Failed funds never count toward the paid balance.
        get(payBase(ctx.businessId()) + "?orderId=" + orderId + "&status=COMPLETED", ctx.accessToken())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    // ---------------------------------------------------------------------
    // Unmatched funds reconciliation queue (Phase 2)
    // ---------------------------------------------------------------------

    @Test
    void webhook_unmatchedFund_createsReconciliationItem() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();

        // Signed webhook for a reference we never generated (e.g. plain transfer
        // straight into a business's BYO account) — becomes a staff item, never dropped.
        simWebhook("ext_direct_bank_transfer_1", "succeeded", 5000.0)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("received"));

        get(reconcileBase(ctx.businessId()), ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.providerReference=='ext_direct_bank_transfer_1')].provider")
                        .value(contains("simulator")))
                .andExpect(jsonPath("$.content[?(@.providerReference=='ext_direct_bank_transfer_1')].amount")
                        .value(contains(5000.0)))
                .andExpect(jsonPath("$.content[?(@.providerReference=='ext_direct_bank_transfer_1')].status")
                        .value(contains("OPEN")));
    }

    @Test
    void reconciliation_resolve_linksToOrder() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        UUID orderId = newOrder(ctx);

        simWebhook("ext_direct_bank_transfer_2", "succeeded", 5000.0)
                .andExpect(status().isOk());
        String items = get(reconcileBase(ctx.businessId()), ctx.accessToken())
                .andReturn().getResponse().getContentAsString();
        UUID itemId = findItemId(items, "ext_direct_bank_transfer_2");

        post(reconcileBase(ctx.businessId()) + "/" + itemId + "/resolve",
                ctx.accessToken(), Map.of("orderId", orderId.toString(), "notes", "customer typed reference"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.businessId").value(ctx.businessId().toString()))
                .andExpect(jsonPath("$.notes").value("customer typed reference"));

        // Resolved items are scoped to this business and vanish from the global
        // OPEN queue. (Other tests' OPEN items may coexist — the queue is global.)
        get(reconcileBase(ctx.businessId()) + "?status=OPEN", ctx.accessToken())
                .andExpect(jsonPath("$.content[?(@.id=='" + itemId + "')]").isEmpty());
        get(reconcileBase(ctx.businessId()) + "?status=RESOLVED", ctx.accessToken())
                .andExpect(jsonPath("$.content[?(@.id=='" + itemId + "')].status").value(contains("RESOLVED")))
                .andExpect(jsonPath("$.content[?(@.id=='" + itemId + "')].orderId").value(contains(orderId.toString())));
    }

    @Test
    void reconciliation_resolve_unknownItem() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        // BusinessException maps to 400 across the API (see ORDER_NOT_FOUND).
        assertProblemDetail(post(reconcileBase(ctx.businessId()) + "/" + UUID.randomUUID() + "/resolve",
                ctx.accessToken(), Map.of("orderId", UUID.randomUUID().toString())), 400, "RECONCILIATION_NOT_FOUND");
    }

    // ---------------------------------------------------------------------
    // PENDING expiry sweep (Phase 3)
    // ---------------------------------------------------------------------

    @Test
    void sweep_expiresAbandonedPendingRedirect() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        UUID orderId = newOrder(ctx);
        String json = createPendingRedirect(ctx, orderId);
        UUID paymentId = readUuid(json, "$.paymentId");
        get(payBase(ctx.businessId()) + "/" + paymentId, ctx.accessToken())
                .andExpect(jsonPath("$.status").value("PENDING"));

        // Future cutoff sweeps every abandoned PENDING/PENDING authorization.
        int swept = paymentProviderService.sweepExpiredPendingBefore(LocalDateTime.now().plusMinutes(5));
        org.junit.jupiter.api.Assertions.assertTrue(swept >= 1);

        get(payBase(ctx.businessId()) + "/" + paymentId, ctx.accessToken())
                .andExpect(jsonPath("$.status").value("FAILED"));
        // providerStatus rides the list DTO, not the detail DTO.
        get(payBase(ctx.businessId()) + "?orderId=" + orderId + "&status=FAILED", ctx.accessToken())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(paymentId.toString()))
                .andExpect(jsonPath("$.content[0].providerStatus").value("EXPIRED"));
        // Expired funds never count as paid.
        get(payBase(ctx.businessId()) + "?orderId=" + orderId + "&status=COMPLETED", ctx.accessToken())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void sweep_preservesFreshPending_andAmountMismatch() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        UUID orderId = newOrder(ctx);
        String json = createPendingRedirect(ctx, orderId);
        UUID paymentId = readUuid(json, "$.paymentId");

        // A past/near cutoff must NOT sweep a freshly created authorization.
        paymentProviderService.sweepExpiredPendingBefore(LocalDateTime.now().minusSeconds(60));
        get(payBase(ctx.businessId()) + "/" + paymentId, ctx.accessToken())
                .andExpect(jsonPath("$.status").value("PENDING"));

        // And an AMOUNT_MISMATCH (staff review) PENDING must never be auto-expired,
        // even under an aggressive future cutoff.
        String json2 = createPendingRedirect(ctx, orderId);
        UUID mismatchPaymentId = readUuid(json2, "$.paymentId");
        String providerReference2 = readString(json2, "$.providerReference");
        simWebhook(providerReference2, "succeeded", 999.0).andExpect(jsonPath("$.status").value("received"));

        paymentProviderService.sweepExpiredPendingBefore(LocalDateTime.now().plusMinutes(30));
        get(payBase(ctx.businessId()) + "/" + mismatchPaymentId, ctx.accessToken())
                .andExpect(jsonPath("$.status").value("PENDING"));
        // providerStatus rides the list DTO — the staff-review flag survives the sweep.
        get(payBase(ctx.businessId()) + "?orderId=" + orderId + "&status=PENDING", ctx.accessToken())
                .andExpect(jsonPath("$.content[?(@.id=='" + mismatchPaymentId + "')].providerStatus")
                        .value(contains("AMOUNT_MISMATCH")));
    }

    // ---------------------------------------------------------------------
    // Charge idempotency (Phase 3)
    // ---------------------------------------------------------------------

    @Test
    void generatePayment_reissuesActiveLinkIdempotently() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        UUID orderId = newOrder(ctx);

        String first = post(payBase(ctx.businessId()) + "/orders/" + orderId + "/generate",
                ctx.accessToken(), Map.of("provider", "simulator"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        UUID firstId = readUuid(first, "$.paymentId");
        String firstRef = readString(first, "$.providerReference");

        // Second generate for the same order+provider re-presents the SAME link —
        // no duplicate charge authorization is stacked.
        String second = post(payBase(ctx.businessId()) + "/orders/" + orderId + "/generate",
                ctx.accessToken(), Map.of("provider", "simulator"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        assertEquals(firstId, readUuid(second, "$.paymentId"));
        assertEquals(firstRef, readString(second, "$.providerReference"));

        // One pending authorization in the queue, not two.
        get(payBase(ctx.businessId()) + "?orderId=" + orderId + "&status=PENDING", ctx.accessToken())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void generatePayment_idempotencyEndsAfterSettle() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        UUID orderId = newOrder(ctx);

        String first = post(payBase(ctx.businessId()) + "/orders/" + orderId + "/generate",
                ctx.accessToken(), Map.of("provider", "simulator"))
                .andReturn().getResponse().getContentAsString();
        UUID firstId = readUuid(first, "$.paymentId");
        String firstRef = readString(first, "$.providerReference");

        // Settle the first authorization via webhook.
        simWebhook(firstRef, "succeeded", 7.0).andExpect(jsonPath("$.status").value("received"));
        get(payBase(ctx.businessId()) + "/" + firstId, ctx.accessToken())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        // Once its authorization settles (order now fully paid), the idempotency
        // early-return is gone and the paid guard rejects any further generation —
        // proving no duplicate charge can sneak in behind a stale pending.
        assertProblemDetail(post(payBase(ctx.businessId()) + "/orders/" + orderId + "/generate",
                ctx.accessToken(), Map.of("provider", "simulator")), 400, "ORDER_ALREADY_PAID");
    }

    // ---------------------------------------------------------------------
    // Provider refund on a BYO-connected business (Phase 3)
    // ---------------------------------------------------------------------

    @Test
    void refund_providerPaymentOnByoBusiness() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        put(providersBase(ctx.businessId()) + "/simulator/connection", ctx.accessToken(),
                Map.of("mode", "BYO", "secretKey", "sk_test_business_123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connectionMode").value("BYO"));

        // Settle a provider-backed card charge through the BYO connection.
        UUID orderId = newOrder(ctx);
        String json = post(payBase(ctx.businessId()) + "/orders/" + orderId + "/process",
                ctx.accessToken(), Map.of("amount", 7.0, "method", "CARD", "provider", "simulator"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andReturn().getResponse().getContentAsString();
        UUID paymentId = readUuid(json, "$.paymentId");

        // Refund routes through the business's own connection context, never
        // fail-closing merely because the platform key differs.
        post(payBase(ctx.businessId()) + "/" + paymentId + "/refund",
                ctx.accessToken(), Map.of("amount", 7.0, "reason", "Customer request"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.originalPaymentId").value(paymentId.toString()));

        get(payBase(ctx.businessId()) + "/" + paymentId, ctx.accessToken())
                .andExpect(jsonPath("$.status").value("REFUNDED"));
    }
}