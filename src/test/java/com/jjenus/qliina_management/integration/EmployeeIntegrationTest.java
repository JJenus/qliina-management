package com.jjenus.qliina_management.integration;

import com.jjenus.qliina_management.employee.repository.EmployeeTargetRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Employee suite: EmployeeController — /api/v1/{businessId}/employees.
 * (Time & attendance, timesheets, schedules, targets, performance.)
 */
class EmployeeIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private EmployeeTargetRepository targetRepository;

    private String base(UUID businessId) {
        return "/api/v1/" + businessId + "/employees";
    }

    private Map<String, Object> clockInBody(AuthContext ctx) {
        return Map.of("shopId", ctx.shopId().toString());
    }

    /** Creates a user with the given role name in the business; returns their login token. */
    private String createEmployee(AuthContext ctx, String roleName) throws Exception {
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
        body.put("phone", "+1" + (555_400_0000L + counter.incrementAndGet()));
        body.put("firstName", roleName);
        body.put("lastName", "Staff");
        body.put("password", DEFAULT_PASSWORD);
        body.put("confirmPassword", DEFAULT_PASSWORD);
        body.put("roles", List.of(Map.of("roleId", ids.get(idx), "shopId", ctx.shopId().toString())));

        post("/api/v1/" + ctx.businessId() + "/users", ctx.accessToken(), body)
                .andExpect(status().isOk());

        return loginToken(username, DEFAULT_PASSWORD);
    }

    private void clockIn(AuthContext ctx, String token) throws Exception {
        post(base(ctx.businessId()) + "/clock-in", token, clockInBody(ctx))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventType").value("CLOCK_IN"));
    }

    // ---------------------------------------------------------------------
    // Shifts & clocking
    // ---------------------------------------------------------------------

    @Test
    void listShifts_emptyByDefault() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        get(base(ctx.businessId()) + "/shifts", ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void listShifts_byShopAfterClockIn() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        String washer = createEmployee(ctx, "WASHER");
        clockIn(ctx, washer);

        get(base(ctx.businessId()) + "/shifts?shopId=" + ctx.shopId(), ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].status").value("CHECKED_IN"))
                .andExpect(jsonPath("$.content[0].shopId").value(ctx.shopId().toString()));
    }

    @Test
    void listShifts_requiresAuth() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        get(base(ctx.businessId()) + "/shifts", null).andExpect(status().isUnauthorized());
    }

    @Test
    void clockIn_validation() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertValidation(post(base(ctx.businessId()) + "/clock-in", ctx.accessToken(), Map.of()),
                Map.of("shopId", "Shop ID is required"));
    }

    @Test
    void clockOut_noActiveShift() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertProblemDetail(post(base(ctx.businessId()) + "/clock-out", ctx.accessToken(), Map.of()),
                400, "NO_ACTIVE_SHIFT");
    }

    @Test
    void breakFlow_startAndEnd() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        clockIn(ctx, ctx.accessToken());

        post(base(ctx.businessId()) + "/break/start", ctx.accessToken(), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventType").value("BREAK_START"));

        post(base(ctx.businessId()) + "/break/end", ctx.accessToken(), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventType").value("BREAK_END"));
    }

    @Test
    void endBreak_notOnBreak() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        clockIn(ctx, ctx.accessToken());
        assertProblemDetail(post(base(ctx.businessId()) + "/break/end", ctx.accessToken(), null),
                400, "NOT_ON_BREAK");
    }

    @Test
    void startBreak_noActiveShift() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertProblemDetail(post(base(ctx.businessId()) + "/break/start", ctx.accessToken(), null),
                400, "NO_ACTIVE_SHIFT");
    }

    @Test
    void suspend_noActiveShift() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertProblemDetail(post(base(ctx.businessId()) + "/suspend", ctx.accessToken(), null),
                400, "NO_ACTIVE_SHIFT");
    }

    @Test
    void suspendAndResume() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        clockIn(ctx, ctx.accessToken());

        post(base(ctx.businessId()) + "/suspend", ctx.accessToken(), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventType").value("SUSPEND"));

        // Current shift reflects the suspended state.
        get(base(ctx.businessId()) + "/current-shift", ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));

        // Resume requires the account password.
        post(base(ctx.businessId()) + "/resume", ctx.accessToken(), Map.of("password", DEFAULT_PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventType").value("RESUME"));

        get(base(ctx.businessId()) + "/current-shift", ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CHECKED_IN"));
    }

    @Test
    void resume_noSuspendedShift() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertProblemDetail(post(base(ctx.businessId()) + "/resume",
                ctx.accessToken(), Map.of("password", DEFAULT_PASSWORD)),
                400, "NO_SUSPENDED_SHIFT");
    }

    @Test
    void resume_invalidPassword() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        clockIn(ctx, ctx.accessToken());
        post(base(ctx.businessId()) + "/suspend", ctx.accessToken(), null).andExpect(status().isOk());

        assertProblemDetail(post(base(ctx.businessId()) + "/resume",
                ctx.accessToken(), Map.of("password", "WrongPass1!")),
                400, "INVALID_PASSWORD");
    }

    @Test
    void resume_validation() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertValidation(post(base(ctx.businessId()) + "/resume", ctx.accessToken(), Map.of()),
                Map.of("password", "Password is required"));
    }

    @Test
    void currentShift_noActiveShift() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertProblemDetail(get(base(ctx.businessId()) + "/current-shift", ctx.accessToken()),
                400, "NO_ACTIVE_SHIFT");
    }

    // ---------------------------------------------------------------------
    // Timesheets
    // ---------------------------------------------------------------------

    @Test
    void getTimesheet_owner() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        LocalDate today = LocalDate.now();
        get(base(ctx.businessId()) + "/" + ctx.userId() + "/timesheet?startDate=" + today.minusDays(1)
                + "&endDate=" + today, ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeId").value(ctx.userId().toString()))
                .andExpect(jsonPath("$.employeeName").value("Owner One"))
                .andExpect(jsonPath("$.entries").isArray())
                .andExpect(jsonPath("$.summary.totalScheduledMinutes").isNumber())
                .andExpect(jsonPath("$.summary.totalWorkedMinutes").isNumber());
    }

    @Test
    void getTimesheet_employeeNotFound() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertProblemDetail(get(base(ctx.businessId()) + "/" + UUID.randomUUID()
                + "/timesheet?startDate=2026-01-01&endDate=2026-01-31", ctx.accessToken()),
                400, "EMPLOYEE_NOT_FOUND");
    }

    @Test
    void getMyTimesheet() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        LocalDate today = LocalDate.now();
        get(base(ctx.businessId()) + "/timesheet?startDate=" + today.minusDays(1)
                + "&endDate=" + today, ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeId").value(ctx.userId().toString()));
    }

    // ---------------------------------------------------------------------
    // Schedules
    // ---------------------------------------------------------------------

    private Map<String, Object> scheduleBody(AuthContext ctx) {
        Map<String, Object> body = new HashMap<>();
        body.put("employeeId", ctx.userId().toString());
        body.put("shopId", ctx.shopId().toString());
        body.put("date", LocalDate.now().toString());
        body.put("startTime", "09:00");
        body.put("endTime", "17:00");
        body.put("role", "FRONT_DESK");
        return body;
    }

    @Test
    void createSchedule_success() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        post(base(ctx.businessId()) + "/shifts/schedule", ctx.accessToken(), scheduleBody(ctx))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeId").value(ctx.userId().toString()))
                .andExpect(jsonPath("$.shopId").value(ctx.shopId().toString()))
                .andExpect(jsonPath("$.startTime").value("09:00:00"))
                .andExpect(jsonPath("$.endTime").value("17:00:00"))
                .andExpect(jsonPath("$.isRecurring").value(false));
    }

    @Test
    void createSchedule_validation() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertValidation(post(base(ctx.businessId()) + "/shifts/schedule", ctx.accessToken(), Map.of()),
                Map.of(
                        "shopId", "Shop ID is required",
                        "employeeId", "Employee ID is required",
                        "startTime", "Start time is required",
                        "endTime", "End time is required"));
    }

    @Test
    void getSchedule_byShop() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        post(base(ctx.businessId()) + "/shifts/schedule", ctx.accessToken(), scheduleBody(ctx))
                .andExpect(status().isOk());

        LocalDate today = LocalDate.now();
        get(base(ctx.businessId()) + "/shifts/schedule?shopId=" + ctx.shopId()
                + "&startDate=" + today.minusDays(1) + "&endDate=" + today.plusDays(1), ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$[0].employeeId").value(ctx.userId().toString()));
    }

    @Test
    void getMySchedule() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        post(base(ctx.businessId()) + "/shifts/schedule", ctx.accessToken(), scheduleBody(ctx))
                .andExpect(status().isOk());

        LocalDate today = LocalDate.now();
        get(base(ctx.businessId()) + "/schedule?startDate=" + today.minusDays(1)
                + "&endDate=" + today.plusDays(1), ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].employeeId").value(ctx.userId().toString()));
    }

    // ---------------------------------------------------------------------
    // Attendance
    // ---------------------------------------------------------------------

    @Test
    void attendanceReport_withShop() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        LocalDate today = LocalDate.now();
        get(base(ctx.businessId()) + "/attendance?shopId=" + ctx.shopId()
                + "&startDate=" + today.minusDays(7) + "&endDate=" + today, ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shopId").value(ctx.shopId().toString()))
                .andExpect(jsonPath("$.summary.totalEmployees").value(1))
                .andExpect(jsonPath("$.dailyBreakdown.length()").value(8))
                .andExpect(jsonPath("$.employeeBreakdown").isArray());
    }

    // ---------------------------------------------------------------------
    // Targets
    // ---------------------------------------------------------------------

    private Map<String, Object> targetsBody(AuthContext ctx) {
        return Map.of(
                "employeeId", ctx.userId().toString(),
                "targets", List.of(Map.of("date", LocalDate.now().toString(), "metric", "ORDERS", "target", 5)));
    }

    @Test
    void setTargets_success() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        post(base(ctx.businessId()) + "/targets", ctx.accessToken(), targetsBody(ctx))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeId").value(ctx.userId().toString()))
                .andExpect(jsonPath("$.targets[0].metric").value("ORDERS"))
                .andExpect(jsonPath("$.targets[0].target").value(5))
                .andExpect(jsonPath("$.targets[0].achieved").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.summary.totalTargets").value(1));
    }

    @Test
    void setTargets_validation() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertValidation(post(base(ctx.businessId()) + "/targets", ctx.accessToken(), Map.of()),
                Map.of(
                        "employeeId", "Employee ID is required",
                        "targets", "Targets are required"));
    }

    @Test
    void getTargets_mineAndById() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        post(base(ctx.businessId()) + "/targets", ctx.accessToken(), targetsBody(ctx))
                .andExpect(status().isOk());

        LocalDate today = LocalDate.now();
        get(base(ctx.businessId()) + "/targets?startDate=" + today.minusDays(1)
                + "&endDate=" + today.plusDays(1), ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targets.length()").value(1));

        get(base(ctx.businessId()) + "/" + ctx.userId() + "/targets?startDate=" + today.minusDays(1)
                + "&endDate=" + today.plusDays(1), ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targets[0].metric").value("ORDERS"));
    }

    @Test
    void updateTargetAchievement() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        post(base(ctx.businessId()) + "/targets", ctx.accessToken(), targetsBody(ctx))
                .andExpect(status().isOk());

        LocalDate today = LocalDate.now();
        UUID targetId = targetRepository.findByEmployeeIdAndDateRange(
                        ctx.userId(), today.minusDays(1), today.plusDays(1)).get(0).getId();

        put(base(ctx.businessId()) + "/targets/" + targetId + "?actualValue=6&notes=Good", ctx.accessToken(), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.target").value(5))
                .andExpect(jsonPath("$.actual").value(6))
                .andExpect(jsonPath("$.achieved").value(true))
                .andExpect(jsonPath("$.achievementRate").value(120.0));
    }

    @Test
    void updateTargetAchievement_notFound() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertProblemDetail(put(base(ctx.businessId()) + "/targets/" + UUID.randomUUID()
                + "?actualValue=1", ctx.accessToken(), null), 400, "TARGET_NOT_FOUND");
    }

    // ---------------------------------------------------------------------
    // Performance
    // ---------------------------------------------------------------------

    @Test
    void getEmployeePerformance_owner() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        LocalDate today = LocalDate.now();
        get(base(ctx.businessId()) + "/" + ctx.userId() + "/performance?startDate=" + today.minusDays(7)
                + "&endDate=" + today, ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeId").value(ctx.userId().toString()))
                .andExpect(jsonPath("$.role").value("BUSINESS_ADMIN"))
                .andExpect(jsonPath("$.metrics.ordersProcessed").isNumber())
                .andExpect(jsonPath("$.metrics.qualityScore").isNumber())
                .andExpect(jsonPath("$.byDay").isArray())
                .andExpect(jsonPath("$.rank").isNumber());
    }

    @Test
    void getMyPerformance() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        LocalDate today = LocalDate.now();
        get(base(ctx.businessId()) + "/performance?startDate=" + today.minusDays(7)
                + "&endDate=" + today, ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeId").value(ctx.userId().toString()));
    }

    @Test
    void getPerformanceLeaderboard() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        LocalDate today = LocalDate.now();
        get(base(ctx.businessId()) + "/performance/leaderboard?startDate=" + today.minusDays(7)
                + "&endDate=" + today, ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].employeeId").value(ctx.userId().toString()))
                .andExpect(jsonPath("$[0].rank").value(1));
    }

    // ---------------------------------------------------------------------
    // Employee directory
    // ---------------------------------------------------------------------

    @Test
    void getEmployee_owner() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        get(base(ctx.businessId()) + "/" + ctx.userId(), ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ctx.userId().toString()))
                .andExpect(jsonPath("$.firstName").value("Owner"))
                .andExpect(jsonPath("$.role").value("BUSINESS_ADMIN"))
                .andExpect(jsonPath("$.employmentStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.isClockedIn").value(false));
    }

    @Test
    void getEmployee_notFound() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertProblemDetail(get(base(ctx.businessId()) + "/" + UUID.randomUUID(), ctx.accessToken()),
                400, "EMPLOYEE_NOT_FOUND");
    }

    @Test
    void listEmployees_containsOwner() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        get(base(ctx.businessId()), ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(ctx.userId().toString()))
                .andExpect(jsonPath("$.content[0].isActive").value(true));
    }

    @Test
    void listEmployees_search() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        get(base(ctx.businessId()) + "?search=Owner", ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void listEmployees_crossTenantDenied() throws Exception {
        AuthContext a = registerBusinessAndOwner();
        AuthContext b = registerBusinessAndOwner();
        get(base(b.businessId()), a.accessToken())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    // ---------------------------------------------------------------------
    // Shop assignments
    // ---------------------------------------------------------------------

    @Test
    void assignAndRemoveEmployeeFromShop() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        String washer = createEmployee(ctx, "WASHER");
        UUID washerId = extractUuid(get("/api/v1/users/me", washer)
                .andExpect(status().isOk()).andReturn(), "$.id");

        // Get current assignments (empty or a default).
        get(base(ctx.businessId()) + "/" + washerId + "/shops", ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        // Assign the washer to the main shop as primary.
        Map<String, Object> body = Map.of("shopAssignments", List.of(Map.of(
                "shopId", ctx.shopId().toString(),
                "isPrimary", true,
                "roles", new java.util.ArrayList<String>())));
        assertSuccess(post(base(ctx.businessId()) + "/" + washerId + "/shops",
                ctx.accessToken(), body), "Shops assigned");

        // Removing the assignment succeeds.
        assertSuccess(delete(base(ctx.businessId()) + "/" + washerId + "/shops/" + ctx.shopId(),
                ctx.accessToken()), "Removed from shop");
    }
}
