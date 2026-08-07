package com.jjenus.qliina_management.integration;

import com.jjenus.qliina_management.billing.gateway.SimulatedPaymentGateway;
import com.jjenus.qliina_management.billing.model.Subscription;
import com.jjenus.qliina_management.billing.model.SubscriptionFeature;
import com.jjenus.qliina_management.billing.model.SubscriptionStatus;
import com.jjenus.qliina_management.billing.repository.BillingInvoiceRepository;
import com.jjenus.qliina_management.billing.repository.BillingPaymentMethodRepository;
import com.jjenus.qliina_management.billing.repository.SubscriptionFeatureRepository;
import com.jjenus.qliina_management.billing.repository.SubscriptionRepository;
import com.jjenus.qliina_management.billing.service.BillingCycleService;
import com.jjenus.qliina_management.billing.service.BillingFeatureResolver;
import com.jjenus.qliina_management.billing.service.BillingPaymentMethodService;
import com.jjenus.qliina_management.billing.service.CouponService;
import com.jjenus.qliina_management.billing.service.DunningService;
import com.jjenus.qliina_management.billing.service.SubscriptionService;
import com.jjenus.qliina_management.billing.service.UsageService;
import com.jjenus.qliina_management.billing.model.PaymentMethodType;
import com.jjenus.qliina_management.billing.model.InvoiceStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the billing engine (MD §2, §4, §6, §7, §8):
 * trial provisioning, plan changes (proration/deferred downgrade), invoicing
 * and payment via the simulated gateway, coupons, metered usage, dunning, the
 * webhook reconciliation endpoint, and the SKIP LOCKED sweep queries on H2.
 */
class BillingIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private SubscriptionService subscriptionService;
    @Autowired
    private BillingCycleService billingCycleService;
    @Autowired
    private DunningService dunningService;
    @Autowired
    private CouponService couponService;
    @Autowired
    private BillingFeatureResolver featureResolver;
    @Autowired
    private BillingPaymentMethodService paymentMethodService;
    @Autowired
    private SimulatedPaymentGateway simulatedGateway;
    @Autowired
    private SubscriptionRepository subscriptionRepository;
    @Autowired
    private SubscriptionFeatureRepository subscriptionFeatureRepository;
    @Autowired
    private BillingPaymentMethodRepository paymentMethodRepository;
    @Autowired
    private BillingInvoiceRepository invoiceRepository;
    @Autowired
    private UsageService usageService;

    // ---------------------------------------------------------------------
    // Trial provisioning + billing overview
    // ---------------------------------------------------------------------

    @Test
    void registerBusiness_provisionsFreeTrial() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        get("/api/v1/" + ctx.businessId() + "/subscription/billing", ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("TRIALING"))
                .andExpect(jsonPath("$.planName").value("Free"))
                .andExpect(jsonPath("$.price").value(0))
                .andExpect(jsonPath("$.currency").value("NGN"))
                .andExpect(jsonPath("$.cancelAtPeriodEnd").value(false))
                .andExpect(jsonPath("$.trialEndsAt").exists());
    }

    // ---------------------------------------------------------------------
    // Payment methods
    // ---------------------------------------------------------------------

    @Test
    void paymentMethods_addListRemove() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        post("/api/v1/" + ctx.businessId() + "/subscription/payment-methods", ctx.accessToken(),
                Map.of("type", "CARD", "label", "Visa"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("CARD"))
                .andExpect(jsonPath("$.isDefault").value(true));

        MvcResult list = get("/api/v1/" + ctx.businessId() + "/subscription/payment-methods",
                        ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andReturn();
        UUID methodId = extractUuid(list, "$[0].id");

        delete("/api/v1/" + ctx.businessId() + "/subscription/payment-methods/" + methodId,
                        ctx.accessToken())
                .andExpect(status().isNoContent());
        get("/api/v1/" + ctx.businessId() + "/subscription/payment-methods", ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ---------------------------------------------------------------------
    // Plan changes — upgrade prorates & charges, downgrade is deferred (§7)
    // ---------------------------------------------------------------------

    @Test
    void upgrade_proratesAndChargesViaSimulator() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        post("/api/v1/" + ctx.businessId() + "/subscription/payment-methods", ctx.accessToken(),
                Map.of("type", "CARD", "label", "Visa"))
                .andExpect(status().isCreated());

        post("/api/v1/" + ctx.businessId() + "/subscription/change-plan", ctx.accessToken(),
                Map.of("plan", "STARTER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan").value("STARTER"));

        // Subscription is now on the paid plan; proration invoice was charged.
        get("/api/v1/" + ctx.businessId() + "/subscription/billing", ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planName").value("Starter"))
                .andExpect(jsonPath("$.price").value(9900))
                .andExpect(jsonPath("$.status").value("TRIALING"));

        get("/api/v1/" + ctx.businessId() + "/subscription/invoices", ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].status").value("PAID"))
                .andExpect(jsonPath("$.content[0].lineItems.length()").value(2));
    }

    @Test
    void downgrade_isDeferredToRenewal() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        post("/api/v1/" + ctx.businessId() + "/subscription/change-plan", ctx.accessToken(),
                Map.of("plan", "PRO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan").value("PRO"));

        post("/api/v1/" + ctx.businessId() + "/subscription/change-plan", ctx.accessToken(),
                Map.of("plan", "STARTER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan").value("STARTER"));

        // Pending downgrade surfaces; current plan stays PRO until renewal.
        get("/api/v1/" + ctx.businessId() + "/subscription/billing", ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planName").value("Pro"))
                .andExpect(jsonPath("$.pendingPlanName").value("Starter"))
                .andExpect(jsonPath("$.pendingChangeAt").exists());
    }

    // ---------------------------------------------------------------------
    // Cancellation (§6)
    // ---------------------------------------------------------------------

    @Test
    void cancel_atPeriodEnd_thenImmediate() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        post("/api/v1/" + ctx.businessId() + "/subscription/cancel", ctx.accessToken(),
                Map.of("atPeriodEnd", true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelAtPeriodEnd").value(true))
                .andExpect(jsonPath("$.status").value("TRIALING"));

        post("/api/v1/" + ctx.businessId() + "/subscription/cancel", ctx.accessToken(),
                Map.of("atPeriodEnd", false))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELED"));

        // Terminal: no live subscription remains.
        assertProblemDetail(get("/api/v1/" + ctx.businessId() + "/subscription/billing",
                ctx.accessToken()), 400, "SUBSCRIPTION_NOT_FOUND");
    }

    // ---------------------------------------------------------------------
    // Coupons (§4)
    // ---------------------------------------------------------------------

    @Test
    void coupon_validateApplyAndDuplicateRejected() throws Exception {
        String admin = adminToken();
        post("/api/v1/admin/billing/coupons", admin,
                Map.of("code", "WELCOME10", "discountType", "PERCENT", "discountValue", 10,
                        "maxRedemptions", 100, "maxRedemptionsPerBusiness", 1))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        AuthContext ctx = registerBusinessAndOwner();
        get("/api/v1/subscription/coupons/WELCOME10", ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("WELCOME10"));

        post("/api/v1/" + ctx.businessId() + "/subscription/coupons", ctx.accessToken(),
                Map.of("code", "WELCOME10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appliedCouponCode").value("WELCOME10"));

        assertProblemDetail(post("/api/v1/" + ctx.businessId() + "/subscription/coupons",
                ctx.accessToken(), Map.of("code", "WELCOME10")), 400, "COUPON_ALREADY_APPLIED");
        assertProblemDetail(get("/api/v1/subscription/coupons/NOPE", ctx.accessToken()),
                400, "COUPON_NOT_FOUND");
    }

    // ---------------------------------------------------------------------
    // Metered usage
    // ---------------------------------------------------------------------

    @Test
    void usageRecord_recordedAndAggregated() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        post("/api/v1/" + ctx.businessId() + "/subscription/usage", ctx.accessToken(),
                Map.of("featureKey", "api_calls", "quantity", 5.00))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.featureKey").value("api_calls"))
                .andExpect(jsonPath("$.quantity").value(5.0));

        post("/api/v1/" + ctx.businessId() + "/subscription/usage", ctx.accessToken(),
                Map.of("featureKey", "api_calls", "quantity", 3.50))
                .andExpect(status().isOk());

        Subscription sub = subscriptionService.getLiveSubscription(ctx.businessId());
        BigDecimal sum = usageService.usageInPeriod(sub.getId(), "api_calls",
                LocalDateTime.now().minusMinutes(5), LocalDateTime.now().plusMinutes(5));
        assertThat(sum).isEqualByComparingTo("8.50");
    }

    // ---------------------------------------------------------------------
    // Admin plans — soft state, hard delete guard (§5)
    // ---------------------------------------------------------------------

    @Test
    void adminPlans_statusChangeAndDeleteGuards() throws Exception {
        String admin = adminToken();

        // Create an unreferenced plan, deprecate it, then hard-delete it.
        String tmpName = "TMP" + random().toUpperCase();
        MvcResult created = post("/api/v1/admin/billing/plans", admin,
                Map.of("name", tmpName, "price", 1000, "currency", "NGN"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.versions[0].price").value(1000))
                .andReturn();
        UUID tmpId = extractUuid(created, "$.id");

        patch("/api/v1/admin/billing/plans/" + tmpId + "/status", admin,
                Map.of("status", "DEPRECATED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DEPRECATED"));

        delete("/api/v1/admin/billing/plans/" + tmpId, admin)
                .andExpect(status().isNoContent());

        // A plan referenced by a subscription can never be hard-deleted.
        String usedName = "USED" + random().toUpperCase();
        MvcResult used = post("/api/v1/admin/billing/plans", admin,
                Map.of("name", usedName, "price", 1000, "currency", "NGN"))
                .andExpect(status().isCreated())
                .andReturn();
        UUID usedId = extractUuid(used, "$.id");

        AuthContext ctx = registerBusinessAndOwner();
        patch("/api/v1/admin/businesses/" + ctx.businessId() + "/plan", admin,
                Map.of("plan", usedName))
                .andExpect(status().isOk());

        get("/api/v1/" + ctx.businessId() + "/subscription/billing", ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planName").value(usedName));

        assertProblemDetail(delete("/api/v1/admin/billing/plans/" + usedId, admin),
                400, "PLAN_IN_USE");
    }

    // ---------------------------------------------------------------------
    // Webhook ingestion (§8)
    // ---------------------------------------------------------------------

    @Test
    void webhook_chargeSucceeded_acceptedIdempotently() throws Exception {
        String payload = "{\"txn\":\"sim_webhook_1\",\"event\":\"charge.succeeded\",\"amount\":100.00,\"status\":\"succeeded\"}";
        postWebhook(payload)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("received"));
        // Redelivery is harmless.
        postWebhook(payload).andExpect(status().isOk());
    }

    @Test
    void webhook_badSignature_rejected() throws Exception {
        String payload = "{\"txn\":\"sim_bad\",\"event\":\"charge.succeeded\",\"amount\":1.00,\"status\":\"succeeded\"}";
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/webhooks/payment/simulator")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("x-sim-secret", "wrong-secret")
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_WEBHOOK_SIGNATURE"));
    }

    private org.springframework.test.web.servlet.ResultActions postWebhook(String payload) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/webhooks/payment/simulator")
                .contentType(MediaType.APPLICATION_JSON)
                .header("x-sim-secret", "sim-secret")
                .content(payload));
    }

    // ---------------------------------------------------------------------
    // Renewal cycle (§6)
    // ---------------------------------------------------------------------

    @Test
    void renewal_billsAndAdvancesPeriod() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        paymentMethodService.add(ctx.businessId(), PaymentMethodType.CARD, "Visa", null, null);

        Subscription sub = subscriptionService.getLiveSubscription(ctx.businessId());
        subscriptionService.transition(sub, SubscriptionStatus.ACTIVE, "manual activation");
        sub = subscriptionRepository.findById(sub.getId()).orElseThrow();
        LocalDateTime pastEnd = LocalDateTime.now().minusMinutes(1);
        sub.setCurrentPeriodStart(LocalDateTime.now().minusMonths(2));
        sub.setCurrentPeriodEnd(pastEnd);
        subscriptionRepository.save(sub);

        boolean renewed = billingCycleService.renew(sub.getId());
        assertThat(renewed).isTrue();

        Subscription after = subscriptionRepository.findById(sub.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(after.getCurrentPeriodEnd()).isAfter(LocalDateTime.now());
        assertThat(after.getRetryCount()).isZero();

        var invoices = invoiceRepository.findAllBySubscriptionIdOrderByIssuedAtDesc(
                sub.getId(), PageRequest.of(0, 10));
        assertThat(invoices.getTotalElements()).isEqualTo(1);
        assertThat(invoices.getContent().get(0).getStatus()).isEqualTo(InvoiceStatus.PAID);
    }

    // ---------------------------------------------------------------------
    // Trial expiry (§6)
    // ---------------------------------------------------------------------

    @Test
    void trialExpiry_cancelsWithoutPaymentMethod() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        Subscription sub = subscriptionService.getLiveSubscription(ctx.businessId());
        sub.setTrialEndsAt(LocalDateTime.now().minusMinutes(1));
        subscriptionRepository.save(sub);

        billingCycleService.processExpiredTrial(sub.getId());
        Subscription after = subscriptionRepository.findById(sub.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(SubscriptionStatus.CANCELED);
    }

    @Test
    void trialExpiry_convertsWithPaymentMethod() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        paymentMethodService.add(ctx.businessId(), PaymentMethodType.CARD, "Visa", null, null);
        Subscription sub = subscriptionService.getLiveSubscription(ctx.businessId());
        sub.setTrialEndsAt(LocalDateTime.now().minusMinutes(1));
        subscriptionRepository.save(sub);

        billingCycleService.processExpiredTrial(sub.getId());
        Subscription after = subscriptionRepository.findById(sub.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(after.getTrialEndsAt()).isNull();
        assertThat(after.getCurrentPeriodEnd()).isAfter(LocalDateTime.now());
    }

    // ---------------------------------------------------------------------
    // Dunning (§6)
    // ---------------------------------------------------------------------

    private Subscription activatePastDue(AuthContext ctx) throws Exception {
        paymentMethodService.add(ctx.businessId(), PaymentMethodType.CARD, "Visa", null, null);
        // FREE is price 0 and auto-pays at renewal; move to a paid plan so the
        // renewal actually fails at the gateway and flips the row to PAST_DUE.
        subscriptionService.changePlan(ctx.businessId(), "STARTER");
        Subscription sub = subscriptionRepository.findById(
                subscriptionService.getLiveSubscription(ctx.businessId()).getId()).orElseThrow();
        subscriptionService.transition(sub, SubscriptionStatus.ACTIVE, "manual activation");
        sub = subscriptionRepository.findById(sub.getId()).orElseThrow();
        sub.setCurrentPeriodStart(LocalDateTime.now().minusMonths(2));
        sub.setCurrentPeriodEnd(LocalDateTime.now().minusMinutes(1));
        subscriptionRepository.save(sub);
        simulatedGateway.forceFailure(true);
        try {
            billingCycleService.renew(sub.getId());
        } finally {
            simulatedGateway.forceFailure(false);
        }
        Subscription pd = subscriptionRepository.findById(sub.getId()).orElseThrow();
        assertThat(pd.getStatus()).isEqualTo(SubscriptionStatus.PAST_DUE);
        assertThat(pd.getRetryCount()).isEqualTo(1);
        return pd;
    }

    @Test
    void dunning_recoversWhenGatewayRecovers() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        Subscription pd = activatePastDue(ctx);

        pd.setNextRetryAt(LocalDateTime.now().minusMinutes(1));
        subscriptionRepository.save(pd);

        dunningService.retry(pd.getId());

        Subscription after = subscriptionRepository.findById(pd.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(after.getRetryCount()).isZero();
        assertThat(after.getNextRetryAt()).isNull();
    }

    @Test
    void dunning_exhaustsAndCancels() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        Subscription pd = activatePastDue(ctx);

        // Attempts 2-4 walk the ladder [1,3,7,14] and stay past_due.
        for (int i = 0; i < 3; i++) {
            pd = subscriptionRepository.findById(pd.getId()).orElseThrow();
            pd.setNextRetryAt(LocalDateTime.now().minusMinutes(1));
            subscriptionRepository.save(pd);
            simulatedGateway.forceFailure(true);
            try {
                dunningService.retry(pd.getId());
            } finally {
                simulatedGateway.forceFailure(false);
            }
            Subscription after = subscriptionRepository.findById(pd.getId()).orElseThrow();
            assertThat(after.getStatus()).isEqualTo(SubscriptionStatus.PAST_DUE);
        }

        // The 5th failed attempt exceeds the ladder [1,3,7,14] → cancel.
        pd = subscriptionRepository.findById(pd.getId()).orElseThrow();
        pd.setNextRetryAt(LocalDateTime.now().minusMinutes(1));
        subscriptionRepository.save(pd);
        simulatedGateway.forceFailure(true);
        try {
            dunningService.retry(pd.getId());
        } finally {
            simulatedGateway.forceFailure(false);
        }
        Subscription after = subscriptionRepository.findById(pd.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(SubscriptionStatus.CANCELED);
    }

    // ---------------------------------------------------------------------
    // Coupon atomicity (§4)
    // ---------------------------------------------------------------------

    @Test
    void couponRedemption_exhaustsAtomically() throws Exception {
        String admin = adminToken();
        post("/api/v1/admin/billing/coupons", admin,
                Map.of("code", "ONLYONE", "discountType", "FIXED_AMOUNT", "discountValue", 1000,
                        "maxRedemptions", 1))
                .andExpect(status().isCreated());

        AuthContext first = registerBusinessAndOwner();
        post("/api/v1/" + first.businessId() + "/subscription/coupons", first.accessToken(),
                Map.of("code", "ONLYONE"))
                .andExpect(status().isOk());

        AuthContext second = registerBusinessAndOwner();
        assertProblemDetail(post("/api/v1/" + second.businessId() + "/subscription/coupons",
                second.accessToken(), Map.of("code", "ONLYONE")), 400, "COUPON_EXHAUSTED");
    }

    // ---------------------------------------------------------------------
    // Feature resolver — plan defaults + per-subscription override
    // ---------------------------------------------------------------------

    @Test
    void featureResolver_planLimitsAndOverrides() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertThat(featureResolver.intValue(ctx.businessId(), "max_shops", -1)).isEqualTo(1);
        assertThat(featureResolver.intValue(ctx.businessId(), "max_users", -1)).isEqualTo(3);
        assertThat(featureResolver.boolValue(ctx.businessId(), "advancedAnalytics", true)).isFalse();

        Subscription sub = subscriptionService.getLiveSubscription(ctx.businessId());
        subscriptionFeatureRepository.save(SubscriptionFeature.builder()
                .subscription(sub)
                .featureKey("max_shops")
                .value("99")
                .overriddenAt(LocalDateTime.now())
                .build());

        assertThat(featureResolver.intValue(ctx.businessId(), "max_shops", -1)).isEqualTo(99);
    }

    // ---------------------------------------------------------------------
    // SKIP LOCKED sweep queries must execute on the test database
    // ---------------------------------------------------------------------

    @Test
    void sweepQueries_executeOnH2() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        Subscription sub = subscriptionService.getLiveSubscription(ctx.businessId());
        sub.setTrialEndsAt(LocalDateTime.now().minusDays(1));
        subscriptionRepository.save(sub);

        List<Subscription> expired = subscriptionRepository.lockExpiredTrials(LocalDateTime.now());
        assertThat(expired).extracting(Subscription::getId).contains(sub.getId());
    }
}
