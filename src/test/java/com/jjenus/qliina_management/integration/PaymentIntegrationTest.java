package com.jjenus.qliina_management.integration;

import com.jjenus.qliina_management.business.service.ServiceCatalogService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Payment suite: PaymentController — /api/v1/{businessId}/payments
 * (payments, refunds, cash drawer, corporate accounts, invoices).
 */
class PaymentIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private ServiceCatalogService catalogService;

    private String payBase(UUID businessId) {
        return "/api/v1/" + businessId + "/payments";
    }

    private String newPhone() {
        return "+1" + (555_720_0000L + counter.incrementAndGet());
    }

    private UUID createCustomer(AuthContext ctx) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("firstName", "Pay");
        body.put("lastName", "Customer");
        body.put("phone", newPhone());
        String json = post("/api/v1/" + ctx.businessId() + "/customers", ctx.accessToken(), body)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return readUuid(json, "$.id");
    }

    private Map<String, Object> orderBody(UUID customerId, AuthContext ctx) {
        UUID serviceTypeId = catalogService.getActiveServices(ctx.businessId()).get(0).getId();
        UUID garmentTypeId = catalogService.getActiveGarments(ctx.businessId()).get(0).getId();
        Map<String, Object> body = new HashMap<>();
        body.put("customerId", customerId.toString());
        body.put("shopId", ctx.shopId().toString());
        body.put("items", List.of(Map.of(
                "serviceTypeId", serviceTypeId.toString(),
                "garmentTypeId", garmentTypeId.toString(),
                "quantity", 2,
                "unitPrice", 3.50,
                "description", "Two shirts")));
        return body;
    }

    /** Creates an order (total 7.0) and returns its id. */
    private UUID createOrder(AuthContext ctx) throws Exception {
        UUID custId = createCustomer(ctx);
        ResultActions rs = post("/api/v1/" + ctx.businessId() + "/orders", ctx.accessToken(),
                orderBody(custId, ctx));
        rs.andExpect(status().isOk());
        return extractUuid(rs.andReturn(), "$.id");
    }

    /** Creates an order and pays it in full (7.0). Returns the payment id. */
    private UUID createPaidOrder(AuthContext ctx) throws Exception {
        UUID orderId = createOrder(ctx);
        String json = post(payBase(ctx.businessId()) + "/orders/" + orderId + "/process",
                ctx.accessToken(), Map.of("amount", 7.0, "method", "CASH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn().getResponse().getContentAsString();
        return readUuid(json, "$.paymentId");
    }

    private UUID createCorporateAccount(AuthContext ctx, UUID customerId) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("customerId", customerId.toString());
        body.put("companyName", "ACME Corp");
        body.put("creditLimit", 500.0);
        String json = post(payBase(ctx.businessId()) + "/corporate-accounts", ctx.accessToken(), body)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return readUuid(json, "$.id");
    }

    // ---------------------------------------------------------------------
    // Payments
    // ---------------------------------------------------------------------

    @Test
    void listPayments_emptyInitially() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        get(payBase(ctx.businessId()), ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void listPayments_afterPayment() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        UUID orderId = createOrder(ctx);
        post(payBase(ctx.businessId()) + "/orders/" + orderId + "/process",
                ctx.accessToken(), Map.of("amount", 7.0, "method", "CASH"))
                .andExpect(status().isOk());

        get(payBase(ctx.businessId()), ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].amount").value(7.0))
                .andExpect(jsonPath("$.content[0].method").value("CASH"))
                .andExpect(jsonPath("$.content[0].status").value("COMPLETED"));
    }

    @Test
    void getPayment_notFound() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertProblemDetail(get(payBase(ctx.businessId()) + "/" + UUID.randomUUID(), ctx.accessToken()),
                400, "PAYMENT_NOT_FOUND");
    }

    @Test
    void getPayment_afterProcess() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        UUID paymentId = createPaidOrder(ctx);

        get(payBase(ctx.businessId()) + "/" + paymentId, ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(paymentId.toString()))
                .andExpect(jsonPath("$.orderId").exists())
                .andExpect(jsonPath("$.amount").value(7.0))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.orderDetails.total").value(7.0))
                .andExpect(jsonPath("$.orderDetails.customer.id").exists())
                .andExpect(jsonPath("$.refunds").isArray());
    }

    @Test
    void processPayment_fullPayment() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        UUID orderId = createOrder(ctx);

        post(payBase(ctx.businessId()) + "/orders/" + orderId + "/process",
                ctx.accessToken(), Map.of("amount", 7.0, "method", "CASH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.paymentId").exists())
                .andExpect(jsonPath("$.amount").value(7.0))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.balanceDue").value(0.0))
                .andExpect(jsonPath("$.isFullyPaid").value(true));
    }

    @Test
    void processPayment_partialPayment() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        UUID orderId = createOrder(ctx);

        post(payBase(ctx.businessId()) + "/orders/" + orderId + "/process",
                ctx.accessToken(), Map.of("amount", 3.0, "method", "CARD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.balanceDue").value(4.0))
                .andExpect(jsonPath("$.isFullyPaid").value(false));

        // Remaining balance can be settled.
        post(payBase(ctx.businessId()) + "/orders/" + orderId + "/process",
                ctx.accessToken(), Map.of("amount", 4.0, "method", "TRANSFER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isFullyPaid").value(true))
                .andExpect(jsonPath("$.balanceDue").value(0.0));
    }

    @Test
    void processPayment_cashWithChange() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        UUID orderId = createOrder(ctx);

        post(payBase(ctx.businessId()) + "/orders/" + orderId + "/process",
                ctx.accessToken(), Map.of("amount", 7.0, "method", "CASH", "cashReceived", 10.0))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.change").value(3.0));
    }

    @Test
    void processPayment_invalidMethod() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        UUID orderId = createOrder(ctx);
        assertProblemDetail(post(payBase(ctx.businessId()) + "/orders/" + orderId + "/process",
                ctx.accessToken(), Map.of("amount", 7.0, "method", "BITCOIN")),
                400, "INVALID_PAYMENT_METHOD");
    }

    @Test
    void processPayment_orderNotFound() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertProblemDetail(post(payBase(ctx.businessId()) + "/orders/" + UUID.randomUUID() + "/process",
                ctx.accessToken(), Map.of("amount", 7.0, "method", "CASH")),
                400, "ORDER_NOT_FOUND");
    }

    @Test
    void processPayment_validation_requiredFields() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        UUID orderId = createOrder(ctx);
        assertValidation(post(payBase(ctx.businessId()) + "/orders/" + orderId + "/process",
                ctx.accessToken(), Map.of()),
                Map.of(
                        "amount", "Amount is required",
                        "method", "Payment method is required"));
    }

    @Test
    void splitPayment_twoMethodsSuccess() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        UUID orderId = createOrder(ctx);

        Map<String, Object> body = Map.of("payments", List.of(
                Map.of("amount", 3.0, "method", "CASH"),
                Map.of("amount", 4.0, "method", "CARD")));

        post(payBase(ctx.businessId()) + "/orders/" + orderId + "/split",
                ctx.accessToken(), body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.amount").value(7.0))
                .andExpect(jsonPath("$.isFullyPaid").value(true))
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    void splitPayment_withInvalidSplitPartial() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        UUID orderId = createOrder(ctx);

        Map<String, Object> body = Map.of("payments", List.of(
                Map.of("amount", 3.0, "method", "CASH"),
                Map.of("amount", 1.0, "method", "BITCOIN")));

        post(payBase(ctx.businessId()) + "/orders/" + orderId + "/split",
                ctx.accessToken(), body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value("PARTIAL"))
                .andExpect(jsonPath("$.errors[0]").exists());
    }

    @Test
    void splitPayment_validation_requiredPayments() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        UUID orderId = createOrder(ctx);
        assertValidation(post(payBase(ctx.businessId()) + "/orders/" + orderId + "/split",
                ctx.accessToken(), Map.of()), Map.of("payments", "Payments are required"));
    }

    // ---------------------------------------------------------------------
    // Refunds
    // ---------------------------------------------------------------------

    @Test
    void processRefund_fullRefund() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        UUID paymentId = createPaidOrder(ctx);

        post(payBase(ctx.businessId()) + "/" + paymentId + "/refund",
                ctx.accessToken(), Map.of("amount", 7.0, "reason", "Overcharged"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refundId").exists())
                .andExpect(jsonPath("$.originalPaymentId").value(paymentId.toString()))
                .andExpect(jsonPath("$.amount").value(7.0))
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        // Payment is now fully refunded.
        get(payBase(ctx.businessId()) + "/" + paymentId, ctx.accessToken())
                .andExpect(jsonPath("$.status").value("REFUNDED"))
                .andExpect(jsonPath("$.refunds[0].reason").value("Overcharged"));
    }

    @Test
    void processRefund_partialThenExceeds() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        UUID paymentId = createPaidOrder(ctx);

        post(payBase(ctx.businessId()) + "/" + paymentId + "/refund",
                ctx.accessToken(), Map.of("amount", 3.0, "reason", "Adjustment"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        get(payBase(ctx.businessId()) + "/" + paymentId, ctx.accessToken())
                .andExpect(jsonPath("$.status").value("PARTIALLY_REFUNDED"));

        assertProblemDetail(post(payBase(ctx.businessId()) + "/" + paymentId + "/refund",
                ctx.accessToken(), Map.of("amount", 5.0, "reason", "Too much")),
                400, "REFUND_LIMIT_EXCEEDED");
    }

    @Test
    void processRefund_exceedsPaymentAmount() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        UUID paymentId = createPaidOrder(ctx);
        assertProblemDetail(post(payBase(ctx.businessId()) + "/" + paymentId + "/refund",
                ctx.accessToken(), Map.of("amount", 8.0, "reason", "Wrong")),
                400, "INVALID_REFUND_AMOUNT");
    }

    @Test
    void processRefund_paymentNotFound() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertProblemDetail(post(payBase(ctx.businessId()) + "/" + UUID.randomUUID() + "/refund",
                ctx.accessToken(), Map.of("amount", 5.0, "reason", "r")),
                400, "PAYMENT_NOT_FOUND");
    }

    @Test
    void processRefund_validation_requiredFields() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        UUID paymentId = createPaidOrder(ctx);
        assertValidation(post(payBase(ctx.businessId()) + "/" + paymentId + "/refund",
                ctx.accessToken(), Map.of()),
                Map.of(
                        "amount", "Amount is required",
                        "reason", "Reason is required"));
    }

    // ---------------------------------------------------------------------
    // Payment methods (via PaymentController /methods)
    // ---------------------------------------------------------------------

    @Test
    void getPaymentMethods_returnsDefaults() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        get(payBase(ctx.businessId()) + "/methods", ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(3)))
                .andExpect(jsonPath("$[?(@.type=='CASH')]").exists())
                .andExpect(jsonPath("$[?(@.type=='CARD')]").exists());
    }

    @Test
    void getPaymentMethods_requiresAuth() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        get(payBase(ctx.businessId()) + "/methods", null).andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------------
    // Cash drawer
    // ---------------------------------------------------------------------

    @Test
    void cashDrawer_openAndList() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        String json = post(payBase(ctx.businessId()) + "/drawer-sessions/open", ctx.accessToken(),
                Map.of("shopId", ctx.shopId().toString(), "startingCash", 100.0))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.shopId").value(ctx.shopId().toString()))
                .andExpect(jsonPath("$.startingCash").value(100.0))
                .andReturn().getResponse().getContentAsString();
        UUID sessionId = readUuid(json, "$.id");

        get(payBase(ctx.businessId()) + "/drawer-sessions?status=OPEN", ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(sessionId.toString()));

        // Second open for the same shop is rejected.
        assertProblemDetail(post(payBase(ctx.businessId()) + "/drawer-sessions/open", ctx.accessToken(),
                Map.of("shopId", ctx.shopId().toString(), "startingCash", 50.0)),
                400, "DRAWER_ALREADY_OPEN");
    }

    @Test
    void cashDrawer_open_validation() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertValidation(post(payBase(ctx.businessId()) + "/drawer-sessions/open",
                ctx.accessToken(), Map.of()),
                Map.of(
                        "shopId", "Shop ID is required",
                        "startingCash", "Starting cash is required"));
    }

    @Test
    void cashDrawer_closeReconciled() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        String openJson = post(payBase(ctx.businessId()) + "/drawer-sessions/open", ctx.accessToken(),
                Map.of("shopId", ctx.shopId().toString(), "startingCash", 100.0))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        UUID sessionId = readUuid(openJson, "$.id");

        // countedBy must be a real user; the owner is fine.
        post(payBase(ctx.businessId()) + "/drawer-sessions/close", ctx.accessToken(),
                Map.of("sessionId", sessionId.toString(), "actualCash", 100.0, "countedBy", ctx.userId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECONCILED"))
                .andExpect(jsonPath("$.difference").value(0.0));
    }

    @Test
    void cashDrawer_closeAlreadyClosed() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        String openJson = post(payBase(ctx.businessId()) + "/drawer-sessions/open", ctx.accessToken(),
                Map.of("shopId", ctx.shopId().toString(), "startingCash", 100.0))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        UUID sessionId = readUuid(openJson, "$.id");

        Map<String, Object> closeBody = Map.of(
                "sessionId", sessionId.toString(), "actualCash", 100.0, "countedBy", ctx.userId().toString());
        post(payBase(ctx.businessId()) + "/drawer-sessions/close", ctx.accessToken(), closeBody)
                .andExpect(status().isOk());

        assertProblemDetail(post(payBase(ctx.businessId()) + "/drawer-sessions/close",
                ctx.accessToken(), closeBody), 400, "SESSION_NOT_OPEN");
    }

    @Test
    void cashDrawer_closeSessionNotFound() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertProblemDetail(post(payBase(ctx.businessId()) + "/drawer-sessions/close", ctx.accessToken(),
                Map.of("sessionId", UUID.randomUUID().toString(), "actualCash", 100.0,
                        "countedBy", ctx.userId().toString())),
                400, "SESSION_NOT_FOUND");
    }

    @Test
    void cashDrawer_close_validation() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertValidation(post(payBase(ctx.businessId()) + "/drawer-sessions/close",
                ctx.accessToken(), Map.of()),
                Map.of(
                        "sessionId", "Session ID is required",
                        "actualCash", "Actual cash is required",
                        "countedBy", "Second person verification is required"));
    }

    // ---------------------------------------------------------------------
    // Corporate accounts & invoices
    // ---------------------------------------------------------------------

    @Test
    void corporateAccount_createListGet() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        UUID customerId = createCustomer(ctx);
        UUID accountId = createCorporateAccount(ctx, customerId);

        get(payBase(ctx.businessId()) + "/corporate-accounts", ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].companyName").value("ACME Corp"))
                .andExpect(jsonPath("$.content[0].status").value("ACTIVE"));

        get(payBase(ctx.businessId()) + "/corporate-accounts/" + accountId, ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(accountId.toString()))
                .andExpect(jsonPath("$.customerId").value(customerId.toString()))
                .andExpect(jsonPath("$.currentBalance").value(0.0))
                .andExpect(jsonPath("$.availableCredit").value(500.0));
    }

    @Test
    void corporateAccount_duplicateRejected() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        UUID customerId = createCustomer(ctx);
        createCorporateAccount(ctx, customerId);

        assertProblemDetail(post(payBase(ctx.businessId()) + "/corporate-accounts", ctx.accessToken(),
                Map.of("customerId", customerId.toString(), "companyName", "ACME Corp",
                        "creditLimit", 500.0)),
                400, "ACCOUNT_EXISTS");
    }

    @Test
    void corporateAccount_customerNotFound() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertProblemDetail(post(payBase(ctx.businessId()) + "/corporate-accounts", ctx.accessToken(),
                Map.of("customerId", UUID.randomUUID().toString(), "companyName", "ACME Corp",
                        "creditLimit", 500.0)),
                400, "CUSTOMER_NOT_FOUND");
    }

    @Test
    void corporateAccount_validation() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertValidation(post(payBase(ctx.businessId()) + "/corporate-accounts",
                ctx.accessToken(), Map.of()),
                Map.of(
                        "customerId", "Customer ID is required",
                        "companyName", "Company name is required",
                        "creditLimit", "Credit limit is required"));
    }

    @Test
    void corporateAccount_getNotFound() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertProblemDetail(get(payBase(ctx.businessId()) + "/corporate-accounts/" + UUID.randomUUID(),
                ctx.accessToken()), 400, "ACCOUNT_NOT_FOUND");
    }

    @Test
    void generateInvoice_forUnpaidOrders() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        UUID customerId = createCustomer(ctx);
        UUID accountId = createCorporateAccount(ctx, customerId);

        // Order must belong to the same customer as the corporate account.
        post("/api/v1/" + ctx.businessId() + "/orders", ctx.accessToken(), orderBody(customerId, ctx))
                .andExpect(status().isOk());

        LocalDate today = LocalDate.now();
        Map<String, Object> body = new HashMap<>();
        body.put("accountId", accountId.toString());
        body.put("periodStart", today.minusDays(1).toString());
        body.put("periodEnd", today.plusDays(1).toString());
        body.put("dueDate", today.plusDays(7).toString());

        post(payBase(ctx.businessId()) + "/corporate-accounts/" + accountId + "/invoices/generate",
                ctx.accessToken(), body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoiceNumber").exists())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.companyName").value("ACME Corp"))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.total").exists());

        // Invoice now shows up in the list.
        get(payBase(ctx.businessId()) + "/invoices", ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void generateInvoice_noOrders() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        UUID customerId = createCustomer(ctx);
        UUID accountId = createCorporateAccount(ctx, customerId);

        LocalDate today = LocalDate.now();
        assertProblemDetail(post(payBase(ctx.businessId()) + "/corporate-accounts/" + accountId
                + "/invoices/generate", ctx.accessToken(),
                Map.of("periodStart", today.toString(), "periodEnd", today.toString())),
                400, "NO_ORDERS");
    }

    @Test
    void generateInvoice_accountNotFound() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        LocalDate today = LocalDate.now();
        assertProblemDetail(post(payBase(ctx.businessId()) + "/corporate-accounts/" + UUID.randomUUID()
                + "/invoices/generate", ctx.accessToken(),
                Map.of("periodStart", today.toString(), "periodEnd", today.toString())),
                400, "ACCOUNT_NOT_FOUND");
    }

    @Test
    void listInvoices_emptyInitially() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        get(payBase(ctx.businessId()) + "/invoices", ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void processOverdueInvoices_success() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertSuccess(post(payBase(ctx.businessId()) + "/invoices/process-overdue",
                ctx.accessToken(), null), "Overdue invoices processed successfully");
    }
}
