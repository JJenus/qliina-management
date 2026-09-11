package com.jjenus.qliina_management.integration;

import com.jjenus.qliina_management.business.service.ServiceCatalogService;
import com.jjenus.qliina_management.employee.model.EmployeeShift;
import com.jjenus.qliina_management.employee.repository.EmployeeShiftRepository;
import com.jjenus.qliina_management.employee.repository.EmployeeTargetRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Reporting suite: ReportController — /api/v1/{businessId}/reports
 * (dashboard, worker dashboard, revenue, profit-loss, aging, tax, sales-by-service,
 * employee performance, export).
 */
class ReportingIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private ServiceCatalogService catalogService;

    @Autowired
    private EmployeeTargetRepository targetRepository;

    @Autowired
    private EmployeeShiftRepository shiftRepository;

    private String base(UUID businessId) {
        return "/api/v1/" + businessId + "/reports";
    }

    private String today() {
        return LocalDate.now().toString();
    }

    private String newPhone() {
        return "+1" + (555_900_0000L + counter.incrementAndGet());
    }

    private UUID createCustomer(AuthContext ctx) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("firstName", "Rep");
        body.put("lastName", "Customer");
        body.put("phone", newPhone());
        String json = post("/api/v1/" + ctx.businessId() + "/customers", ctx.accessToken(), body)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return readUuid(json, "$.id");
    }

    private Map<String, Object> orderBody(UUID customerId, AuthContext ctx) {
        UUID serviceTypeId = catalogService.getActiveServices(ctx.businessId()).get(0).getId();
        UUID garmentTypeId = catalogService.getActiveGarments(ctx.businessId()).get(0).getId();
        return Map.of(
                "customerId", customerId.toString(),
                "shopId", ctx.shopId().toString(),
                "items", List.of(Map.of(
                        "serviceTypeId", serviceTypeId.toString(),
                        "garmentTypeId", garmentTypeId.toString(),
                        "quantity", 2,
                        "unitPrice", 3.50,
                        "description", "Two shirts")));
    }

    /** Creates an order (total 7.0). */
    private UUID createOrder(AuthContext ctx) throws Exception {
        UUID custId = createCustomer(ctx);
        ResultActions rs = post("/api/v1/" + ctx.businessId() + "/orders", ctx.accessToken(),
                orderBody(custId, ctx));
        rs.andExpect(status().isOk());
        return extractUuid(rs.andReturn(), "$.id");
    }

    /** Creates an order and pays it in full (7.0). */
    private void createPaidOrder(AuthContext ctx) throws Exception {
        UUID orderId = createOrder(ctx);
        post("/api/v1/" + ctx.businessId() + "/payments/orders/" + orderId + "/process",
                ctx.accessToken(), Map.of("amount", 7.0, "method", "CASH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    /** Creates a user with the given role name; returns their login token. */
    private String createEmployee(AuthContext ctx, String roleName) throws Exception {
        return createEmployeeWithId(ctx, roleName).token();
    }

    private record CreatedEmployee(UUID id, String token) {}

    /** Creates a user with the given role name; returns both the id and their login token. */
    private CreatedEmployee createEmployeeWithId(AuthContext ctx, String roleName) throws Exception {
        MvcResult rolesRes = get("/api/v1/" + ctx.businessId() + "/users/available-roles", ctx.accessToken())
                .andExpect(status().isOk()).andReturn();
        String rolesJson = rolesRes.getResponse().getContentAsString(StandardCharsets.UTF_8);
        List<String> names = JsonPath.read(rolesJson, "$[*].name");
        List<String> ids = JsonPath.read(rolesJson, "$[*].id");
        int idx = names.indexOf(roleName);
        if (idx < 0) throw new IllegalStateException("Role not found: " + roleName);

        String unique = random();
        String username = roleName.toLowerCase() + "_" + unique;
        Map<String, Object> body = new HashMap<>();
        body.put("username", username);
        body.put("email", username + "@test.com");
        body.put("phone", "+1" + (555_500_0000L + counter.incrementAndGet()));
        body.put("firstName", roleName);
        body.put("lastName", "Staff");
        body.put("password", DEFAULT_PASSWORD);
        body.put("confirmPassword", DEFAULT_PASSWORD);
        body.put("roles", List.of(Map.of("roleId", ids.get(idx), "shopId", ctx.shopId().toString())));

        MvcResult res = post("/api/v1/" + ctx.businessId() + "/users", ctx.accessToken(), body)
                .andExpect(status().isOk()).andReturn();
        UUID userId = readUuid(res.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.id");
        return new CreatedEmployee(userId, loginToken(username, DEFAULT_PASSWORD));
    }

    private void clockIn(AuthContext ctx, String token) throws Exception {
        post("/api/v1/" + ctx.businessId() + "/employees/clock-in", token,
                Map.of("shopId", ctx.shopId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventType").value("CLOCK_IN"));
    }

    // ---------------------------------------------------------------------
    // Dashboard
    // ---------------------------------------------------------------------

    @Test
    void dashboard_emptyBusiness() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        get(base(ctx.businessId()) + "/dashboard", ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").exists())
                .andExpect(jsonPath("$.kpi.todayRevenue").exists())
                .andExpect(jsonPath("$.kpi.revenueChange").exists())
                .andExpect(jsonPath("$.kpi.todayOrders").value(0))
                .andExpect(jsonPath("$.kpi.pendingOrders").exists())
                .andExpect(jsonPath("$.kpi.activeEmployees").exists())
                .andExpect(jsonPath("$.kpi.averageOrderValue").exists())
                .andExpect(jsonPath("$.kpi.outstandingReceivables").exists())
                .andExpect(jsonPath("$.kpi.itemsInQC").exists())
                .andExpect(jsonPath("$.revenueChart").isArray())
                .andExpect(jsonPath("$.ordersChart").isArray())
                .andExpect(jsonPath("$.alerts").isArray());
    }

    @Test
    void dashboard_withData() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        createPaidOrder(ctx);
        createOrder(ctx);

        get(base(ctx.businessId()) + "/dashboard", ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kpi.todayOrders").value(2))
                .andExpect(jsonPath("$.kpi.pendingOrders").value(2))
                .andExpect(jsonPath("$.kpi.outstandingReceivables").value(7.0));
    }

    @Test
    void dashboard_shopFilter() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        createPaidOrder(ctx);

        get(base(ctx.businessId()) + "/dashboard?shopId=" + ctx.shopId(), ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kpi.todayOrders").value(1));
    }

    @Test
    void dashboard_requiresAuth() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        get(base(ctx.businessId()) + "/dashboard", null).andExpect(status().isUnauthorized());
    }

    @Test
    void dashboard_crossTenant_403() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        AuthContext other = registerBusinessAndOwner();
        assertProblemDetail(get(base(ctx.businessId()) + "/dashboard", other.accessToken()),
                403, "ACCESS_DENIED");
    }

    @Test
    void dashboard_washerForbidden_403() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        String washer = createEmployee(ctx, "WASHER");
        assertProblemDetail(get(base(ctx.businessId()) + "/dashboard", washer),
                403, "ACCESS_DENIED");
    }

    // ---------------------------------------------------------------------
    // Worker dashboard
    // ---------------------------------------------------------------------

    @Test
    void workerDashboard_ownerNotWorker_400() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertProblemDetail(get(base(ctx.businessId()) + "/worker-dashboard", ctx.accessToken()),
                400, "NOT_WORKER_ROLE");
    }

    @Test
    void workerDashboard_washerSuccess() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        createPaidOrder(ctx);
        String washer = createEmployee(ctx, "WASHER");
        clockIn(ctx, washer);

        get(base(ctx.businessId()) + "/worker-dashboard", washer)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("WASHER"))
                .andExpect(jsonPath("$.employeeName").exists())
                .andExpect(jsonPath("$.todayMetrics.itemsProcessed").exists())
                .andExpect(jsonPath("$.todayMetrics.itemsPassedQC").exists())
                .andExpect(jsonPath("$.queueSummary.pendingItems").exists())
                .andExpect(jsonPath("$.queueSummary.inProgressItems").exists())
                .andExpect(jsonPath("$.qualityOverview.todayQualityScore").exists())
                .andExpect(jsonPath("$.qualityOverview.recentDefectTypes").isArray())
                .andExpect(jsonPath("$.recentItems").isArray())
                // Divergence: Lombok @Data + boolean field `isClockedIn` serialises as `clockedIn`
                .andExpect(jsonPath("$.shiftInfo.clockedIn").value(true));
    }

    /**
     * Regression: ItemStatusHistoryRepository aliased the timestamp as `ts` but the
     * WorkerEventProjection getter was `getTimestamp()`, so Spring Data returned a
     * null timestamp and both worker-dashboard/history and worker-dashboard 500'd
     * once a worker had actually started/completed work. Drive real work events and
     * assert both endpoints render.
     */
    @Test
    void workerDashboardAndHistory_withRealWorkEvents() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        createPaidOrder(ctx);
        String washer = createEmployee(ctx, "WASHER");
        clockIn(ctx, washer);

        MvcResult queue = get("/api/v1/" + ctx.businessId() + "/worker/orders/items/queue", washer)
                .andExpect(status().isOk()).andReturn();
        String queueJson = queue.getResponse().getContentAsString(StandardCharsets.UTF_8);
        List<String> itemIds = JsonPath.read(queueJson, "$.content[*].id");
        assertThat(itemIds).isNotEmpty();

        String itemId = itemIds.get(0);
        post("/api/v1/" + ctx.businessId() + "/worker/orders/items/" + itemId + "/start", washer, null)
                .andExpect(status().isOk());
        post("/api/v1/" + ctx.businessId() + "/worker/orders/items/" + itemId + "/complete", washer, null)
                .andExpect(status().isOk());

        // History must bucket the completed work event by day (previously NPE'd on null ts).
        get(base(ctx.businessId()) + "/worker-dashboard/history?days=7", washer)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days").value(7))
                .andExpect(jsonPath("$.dailyStats[0].date").exists())
                .andExpect(jsonPath("$.dailyStats[*].itemsProcessed").isArray());

        // Dashboard efficiency pairing (start->complete) must not NPE either.
        get(base(ctx.businessId()) + "/worker-dashboard", washer)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.periodStats.itemsProcessed").value(1));
    }

    @Test
    void workerDashboard_requiresAuth() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        get(base(ctx.businessId()) + "/worker-dashboard", null).andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------------
    // Revenue report
    // ---------------------------------------------------------------------

    @Test
    void revenueReport_success() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        createPaidOrder(ctx);

        get(base(ctx.businessId()) + "/revenue?startDate=" + today() + "&endDate=" + today(),
                ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period.start").value(today()))
                .andExpect(jsonPath("$.period.end").value(today()))
                .andExpect(jsonPath("$.totalRevenue").value(7.0))
                .andExpect(jsonPath("$.totalOrders").value(1))
                .andExpect(jsonPath("$.averageOrderValue").value(7.0))
                .andExpect(jsonPath("$.byPeriod[0].revenue").value(7.0))
                .andExpect(jsonPath("$.byPaymentMethod[0].method").value("CASH"))
                .andExpect(jsonPath("$.byPaymentMethod[0].amount").value(7.0))
                .andExpect(jsonPath("$.byServiceType[0].orders").value(1))
                .andExpect(jsonPath("$.byShop[0].shopId").value(ctx.shopId().toString()));
    }

    @Test
    void revenueReport_groupByWeek() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        createPaidOrder(ctx);

        get(base(ctx.businessId()) + "/revenue?startDate=" + today() + "&endDate=" + today() + "&groupBy=WEEK",
                ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.byPeriod[0].period").value(LocalDate.now()
                        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-'W'ww"))));
    }

    @Test
    void revenueReport_missingDates_400() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertValidation(get(base(ctx.businessId()) + "/revenue", ctx.accessToken()),
                Map.of(
                        "startDate", "startDate is required",
                        "endDate", "endDate is required"));
    }

    // ---------------------------------------------------------------------
    // Profit & Loss
    // ---------------------------------------------------------------------

    @Test
    void profitLoss_success() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        createPaidOrder(ctx);
        post("/api/v1/" + ctx.businessId() + "/expenses", ctx.accessToken(), Map.of(
                "category", "SUPPLIES",
                "description", "Detergent refill",
                "amount", 45.75,
                "expenseDate", today()))
                .andExpect(status().isCreated());

        get(base(ctx.businessId()) + "/profit-loss?startDate=" + today() + "&endDate=" + today(),
                ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period.start").value(today()))
                .andExpect(jsonPath("$.revenue.total").value(7.0))
                .andExpect(jsonPath("$.expenses.total").value(45.75))
                .andExpect(jsonPath("$.expenses.categories[0].category").value("SUPPLIES"))
                .andExpect(jsonPath("$.expenses.categories[0].amount").value(45.75))
                .andExpect(jsonPath("$.grossProfit").value(-38.75))
                .andExpect(jsonPath("$.grossMargin").exists())
                .andExpect(jsonPath("$.netProfit").value(-38.75));
    }

    @Test
    void profitLoss_missingDates_400() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertValidation(get(base(ctx.businessId()) + "/profit-loss", ctx.accessToken()),
                Map.of(
                        "startDate", "startDate is required",
                        "endDate", "endDate is required"));
    }

    // ---------------------------------------------------------------------
    // Aging report
    // ---------------------------------------------------------------------

    @Test
    void agingReport_withUnpaidOrder() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        createOrder(ctx);

        get(base(ctx.businessId()) + "/aging", ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.asOfDate").exists())
                .andExpect(jsonPath("$.totalReceivables").value(7.0))
                .andExpect(jsonPath("$.buckets['current'].amount").value(7.0))
                .andExpect(jsonPath("$.buckets['current'].count").value(1))
                .andExpect(jsonPath("$.byCustomer[0].customerName").value("Rep Customer"))
                .andExpect(jsonPath("$.byCustomer[0].totalDue").value(7.0))
                .andExpect(jsonPath("$.byCustomer[0].current").value(7.0));
    }

    @Test
    void agingReport_emptyBusiness() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        get(base(ctx.businessId()) + "/aging", ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalReceivables").value(0))
                .andExpect(jsonPath("$.byCustomer").isEmpty());
    }

    // ---------------------------------------------------------------------
    // Tax report
    // ---------------------------------------------------------------------

    @Test
    void taxReport_success() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        createPaidOrder(ctx);

        get(base(ctx.businessId()) + "/tax?startDate=" + today() + "&endDate=" + today(),
                ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period.start").value(today()))
                .andExpect(jsonPath("$.totalSales").value(7.0))
                .andExpect(jsonPath("$.taxableSales").value(7.0))
                .andExpect(jsonPath("$.taxRate").value(0))
                .andExpect(jsonPath("$.taxCollected").value(0))
                .andExpect(jsonPath("$.details[0].amount").value(7.0));
    }

    @Test
    void taxReport_missingDates_400() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertValidation(get(base(ctx.businessId()) + "/tax", ctx.accessToken()),
                Map.of(
                        "startDate", "startDate is required",
                        "endDate", "endDate is required"));
    }

    // ---------------------------------------------------------------------
    // Sales by service
    // ---------------------------------------------------------------------

    @Test
    void salesByService_success() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        createPaidOrder(ctx);

        get(base(ctx.businessId()) + "/sales-by-service?startDate=" + today() + "&endDate=" + today(),
                ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.services[0].serviceName").exists())
                .andExpect(jsonPath("$.services[0].orderCount").value(1))
                // itemCount counts OrderItem rows, not summed quantity
                .andExpect(jsonPath("$.services[0].itemCount").value(1))
                .andExpect(jsonPath("$.services[0].revenue").value(7.0))
                .andExpect(jsonPath("$.services[0].percentage").value(100.0))
                .andExpect(jsonPath("$.services[0].averageOrderValue").value(7.0))
                .andExpect(jsonPath("$.dailyBreakdown['ALL'].length()").value(1))
                .andExpect(jsonPath("$.dailyBreakdown['ALL'][0].orders").value(1));
    }

    @Test
    void salesByService_missingDates_400() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertValidation(get(base(ctx.businessId()) + "/sales-by-service", ctx.accessToken()),
                Map.of(
                        "startDate", "startDate is required",
                        "endDate", "endDate is required"));
    }

    // ---------------------------------------------------------------------
    // Employee performance
    // ---------------------------------------------------------------------

    @Test
    void employeePerformance_success() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        createPaidOrder(ctx);
        String washer = createEmployee(ctx, "WASHER");
        clockIn(ctx, washer);

        get(base(ctx.businessId()) + "/employee-performance?startDate=" + today() + "&endDate=" + today(),
                ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].role").value("WASHER"))
                .andExpect(jsonPath("$[0].employeeName").exists())
                .andExpect(jsonPath("$[0].rank").value(1))
                .andExpect(jsonPath("$[0].metrics.ordersProcessed").value(0))
                .andExpect(jsonPath("$[0].metrics.attendanceRate").exists())
                .andExpect(jsonPath("$[0].dailyBreakdown[0].date").value(today()));
    }

    @Test
    void employeePerformance_unknownEmployee_400() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertProblemDetail(get(base(ctx.businessId())
                        + "/employee-performance?startDate=" + today() + "&endDate=" + today()
                        + "&employeeId=" + UUID.randomUUID(), ctx.accessToken()),
                400, "EMPLOYEE_NOT_FOUND");
    }

    @Test
    void employeePerformance_missingDates_400() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertValidation(get(base(ctx.businessId()) + "/employee-performance", ctx.accessToken()),
                Map.of(
                        "startDate", "startDate is required",
                        "endDate", "endDate is required"));
    }

    @Test
    void employeePerformance_shiftDayWithoutOrderItems_no500() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        CreatedEmployee washer = createEmployeeWithId(ctx, "WASHER");
        LocalDate day = LocalDate.now();

        // Deterministic clocked-out shift: worked a full day, no order items in range.
        EmployeeShift shift = new EmployeeShift();
        shift.setBusinessId(ctx.businessId());
        shift.setShopId(ctx.shopId());
        shift.setEmployeeId(washer.id());
        shift.setDate(day);
        shift.setScheduledStart(day.atTime(8, 0));
        shift.setScheduledEnd(day.atTime(17, 0));
        shift.setActualStart(day.atTime(8, 5));
        shift.setActualEnd(day.atTime(17, 0));
        shift.setTotalWorkMinutes(480);
        shift.setStatus(EmployeeShift.ShiftStatus.CHECKED_OUT);
        shiftRepository.save(shift);

        // Regression: productivity unboxed the nullable order-items SUM on a worked
        // shift day (NPE -> 500). Must return 200 with attendance scored from the shift.
        get(base(ctx.businessId()) + "/employee-performance?startDate=" + day + "&endDate=" + day,
                ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].metrics.attendanceRate").value(100.0))
                .andExpect(jsonPath("$[0].metrics.ontimeRate").value(100.0))
                .andExpect(jsonPath("$[0].metrics.productivity").value(0.0));
    }

    @Test
    void employeePerformance_nonShiftTrackedRole_attendanceNotApplicable() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        // SHOP_MANAGER / admins are not clock-in-gated (optional shift) — the report
        // must not score them on attendance/on-time and must not look up shifts for them.
        CreatedEmployee manager = createEmployeeWithId(ctx, "SHOP_MANAGER");

        get(base(ctx.businessId())
                        + "/employee-performance?startDate=" + today() + "&endDate=" + today()
                        + "&employeeId=" + manager.id(),
                ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].metrics.attendanceRate").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$[0].metrics.ontimeRate").value(org.hamcrest.Matchers.nullValue()));
    }

    // ---------------------------------------------------------------------
    // Export
    // ---------------------------------------------------------------------

    @Test
    void export_aging_csv() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        createOrder(ctx);

        post(base(ctx.businessId()) + "/export", ctx.accessToken(),
                Map.of("reportType", "AGING", "format", "CSV"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("Content-Type", "text/csv"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().exists("Content-Disposition"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .content().string(org.hamcrest.Matchers.containsString("Rep Customer")));
    }

    @Test
    void export_revenue_csv() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        createPaidOrder(ctx);

        post(base(ctx.businessId()) + "/export", ctx.accessToken(),
                Map.of("reportType", "REVENUE", "format", "CSV",
                        "parameters", Map.of("startDate", today(), "endDate", today())))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .content().string(org.hamcrest.Matchers.containsString("REVENUE REPORT")));
    }

    @Test
    void export_invalidReportType_400() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertProblemDetail(post(base(ctx.businessId()) + "/export", ctx.accessToken(),
                Map.of("reportType", "FANCY", "format", "CSV")),
                400, "INVALID_REQUEST");
    }

    @Test
    void export_invalidFormat_400() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        // AGING export ignores format; invalid format only fails for report types that switch on it
        assertProblemDetail(post(base(ctx.businessId()) + "/export", ctx.accessToken(),
                Map.of("reportType", "REVENUE", "format", "XML")),
                400, "INVALID_REQUEST");
    }

    @Test
    void export_missingFields_400() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertValidation(post(base(ctx.businessId()) + "/export", ctx.accessToken(), Map.of()),
                Map.of(
                        "reportType", "reportType is required",
                        "format", "format is required"));
    }

    // ---------------------------------------------------------------------
    // Export – CSV per report type
    // ---------------------------------------------------------------------

    @Test
    void export_profitLoss_csv() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        createPaidOrder(ctx);
        post(base(ctx.businessId()) + "/export", ctx.accessToken(),
                Map.of("reportType", "PROFIT_LOSS", "format", "CSV",
                        "parameters", Map.of("startDate", today(), "endDate", today())))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("PROFIT")));
    }

    @Test
    void export_tax_csv() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        createPaidOrder(ctx);
        post(base(ctx.businessId()) + "/export", ctx.accessToken(),
                Map.of("reportType", "TAX", "format", "CSV",
                        "parameters", Map.of("startDate", today(), "endDate", today())))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv"));
    }

    @Test
    void export_salesByService_csv() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        createPaidOrder(ctx);
        post(base(ctx.businessId()) + "/export", ctx.accessToken(),
                Map.of("reportType", "SALES_BY_SERVICE", "format", "CSV",
                        "parameters", Map.of("startDate", today(), "endDate", today())))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv"));
    }

    @Test
    void export_employeePerf_csv() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        String empToken = createEmployee(ctx, "WASHER");
        clockIn(ctx, empToken);
        post(base(ctx.businessId()) + "/export", ctx.accessToken(),
                Map.of("reportType", "EMPLOYEE_PERF", "format", "CSV",
                        "parameters", Map.of("startDate", today(), "endDate", today())))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv"));
    }

    @Test
    void export_employeePerformance_alias_csv() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        post(base(ctx.businessId()) + "/export", ctx.accessToken(),
                Map.of("reportType", "EMPLOYEE_PERFORMANCE", "format", "CSV",
                        "parameters", Map.of("startDate", today(), "endDate", today())))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv"));
    }

    // ---------------------------------------------------------------------
    // Export – EXCEL per report type
    // ---------------------------------------------------------------------

    @Test
    void export_revenue_excel() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        createPaidOrder(ctx);
        post(base(ctx.businessId()) + "/export", ctx.accessToken(),
                Map.of("reportType", "REVENUE", "format", "EXCEL",
                        "parameters", Map.of("startDate", today(), "endDate", today())))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().exists("Content-Disposition"));
    }

    @Test
    void export_profitLoss_excel() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        createPaidOrder(ctx);
        post(base(ctx.businessId()) + "/export", ctx.accessToken(),
                Map.of("reportType", "PROFIT_LOSS", "format", "EXCEL",
                        "parameters", Map.of("startDate", today(), "endDate", today())))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    }

    @Test
    void export_aging_excel() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        createOrder(ctx);
        post(base(ctx.businessId()) + "/export", ctx.accessToken(),
                Map.of("reportType", "AGING", "format", "EXCEL"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    }

    @Test
    void export_tax_excel() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        post(base(ctx.businessId()) + "/export", ctx.accessToken(),
                Map.of("reportType", "TAX", "format", "EXCEL",
                        "parameters", Map.of("startDate", today(), "endDate", today())))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    }

    @Test
    void export_salesByService_excel() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        post(base(ctx.businessId()) + "/export", ctx.accessToken(),
                Map.of("reportType", "SALES_BY_SERVICE", "format", "EXCEL",
                        "parameters", Map.of("startDate", today(), "endDate", today())))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    }

    @Test
    void export_employeePerf_excel() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        post(base(ctx.businessId()) + "/export", ctx.accessToken(),
                Map.of("reportType", "EMPLOYEE_PERF", "format", "EXCEL",
                        "parameters", Map.of("startDate", today(), "endDate", today())))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    }

    // ---------------------------------------------------------------------
    // Export – PDF per report type
    // ---------------------------------------------------------------------

    @Test
    void export_revenue_pdf() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        createPaidOrder(ctx);
        post(base(ctx.businessId()) + "/export", ctx.accessToken(),
                Map.of("reportType", "REVENUE", "format", "PDF",
                        "parameters", Map.of("startDate", today(), "endDate", today())))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("%PDF")));
    }

    @Test
    void export_profitLoss_pdf() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        createPaidOrder(ctx);
        post(base(ctx.businessId()) + "/export", ctx.accessToken(),
                Map.of("reportType", "PROFIT_LOSS", "format", "PDF",
                        "parameters", Map.of("startDate", today(), "endDate", today())))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("%PDF")));
    }

    @Test
    void export_aging_pdf() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        createOrder(ctx);
        post(base(ctx.businessId()) + "/export", ctx.accessToken(),
                Map.of("reportType", "AGING", "format", "PDF"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("%PDF")));
    }

    @Test
    void export_tax_pdf() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        post(base(ctx.businessId()) + "/export", ctx.accessToken(),
                Map.of("reportType", "TAX", "format", "PDF",
                        "parameters", Map.of("startDate", today(), "endDate", today())))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("%PDF")));
    }

    @Test
    void export_salesByService_pdf() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        post(base(ctx.businessId()) + "/export", ctx.accessToken(),
                Map.of("reportType", "SALES_BY_SERVICE", "format", "PDF",
                        "parameters", Map.of("startDate", today(), "endDate", today())))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("%PDF")));
    }

    @Test
    void export_employeePerf_pdf() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        post(base(ctx.businessId()) + "/export", ctx.accessToken(),
                Map.of("reportType", "EMPLOYEE_PERF", "format", "PDF",
                        "parameters", Map.of("startDate", today(), "endDate", today())))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("%PDF")));
    }

    // ---------------------------------------------------------------------
    // Export – Content-Disposition filename format
    // ---------------------------------------------------------------------

    @Test
    void export_contentDisposition_filename() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        String date = LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
        post(base(ctx.businessId()) + "/export", ctx.accessToken(),
                Map.of("reportType", "AGING", "format", "CSV"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("aging_" + date + ".csv")));
    }

    @Test
    void export_aging_csv_neutralizesFormulaInjection() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        Map<String, Object> body = new HashMap<>();
        body.put("firstName", "=1+1");
        body.put("lastName", "Mal");
        body.put("phone", newPhone());
        UUID custId = readUuid(post("/api/v1/" + ctx.businessId() + "/customers", ctx.accessToken(), body)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.id");
        post("/api/v1/" + ctx.businessId() + "/orders", ctx.accessToken(), orderBody(custId, ctx))
                .andExpect(status().isOk());

        post(base(ctx.businessId()) + "/export", ctx.accessToken(),
                Map.of("reportType", "AGING", "format", "CSV"))
                .andExpect(status().isOk())
                // Formula-significant cells are neutralized with an apostrophe prefix…
                .andExpect(content().string(org.hamcrest.Matchers.containsString("'=1+1 Mal")))
                // …and never emitted as a live formula cell (no line starting with raw '=').
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("\r\n=1+1 Mal"))));
    }

    @Test
    void export_profitLoss_excel_writesDecimalEntriesExactly() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        createPaidOrder(ctx);
        post("/api/v1/" + ctx.businessId() + "/expenses", ctx.accessToken(), Map.of(
                "category", "SUPPLIES",
                "description", "Detergent refill",
                "amount", 45.75,
                "expenseDate", today()))
                .andExpect(status().isCreated());

        MvcResult res = post(base(ctx.businessId()) + "/export", ctx.accessToken(),
                Map.of("reportType", "PROFIT_LOSS", "format", "EXCEL",
                        "parameters", Map.of("startDate", today(), "endDate", today())))
                .andExpect(status().isOk())
                .andReturn();

        byte[] xlsx = res.getResponse().getContentAsByteArray();
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(xlsx))) {
            Sheet sheet = wb.getSheetAt(0);
            boolean found = false;
            for (Row row : sheet) {
                Cell category = row.getCell(0);
                if (category != null && "SUPPLIES".equals(category.getStringCellValue())) {
                    Cell amount = row.getCell(1);
                    // BigDecimal amounts are written as exact strings, never IEEE-754 doubles.
                    assertThat(amount.getCellType()).isEqualTo(CellType.STRING);
                    assertThat(amount.getStringCellValue()).isEqualTo("45.75");
                    found = true;
                }
            }
            assertThat(found).as("expense row present in workbook").isTrue();
        }
    }

    // ---------------------------------------------------------------------
    // Auth guard
    // ---------------------------------------------------------------------

    @Test
    void reports_requiresAuth() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        get(base(ctx.businessId()) + "/revenue?startDate=" + today() + "&endDate=" + today(), null)
                .andExpect(status().isUnauthorized());
    }
}
