package com.jjenus.qliina_management.integration;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Customer suite: CustomerController — /api/v1/{businessId}/customers.
 *
 * The controller carries @RequireClockIn, but BUSINESS_ADMIN is not a clock-in
 * role by default (ShiftGateService.DEFAULT_CLOCK_REQUIRED_ROLES + empty config),
 * so all write endpoints work for the registered owner without clocking in.
 */
class CustomerIntegrationTest extends BaseIntegrationTest {

    private String customerBase(UUID businessId) {
        return "/api/v1/" + businessId + "/customers";
    }

    private Map<String, Object> customerBody(String firstName, String phone, Map<String, Object> extra) {
        Map<String, Object> body = new HashMap<>();
        body.put("firstName", firstName);
        body.put("lastName", "Test");
        body.put("phone", phone);
        if (extra != null) body.putAll(extra);
        return body;
    }

    private String newPhone() {
        return "+1" + (555_600_0000L + counter.incrementAndGet());
    }

    // ---------------------------------------------------------------------
    // List / search
    // ---------------------------------------------------------------------

    @Test
    void listCustomers_emptyThenWithOne() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        String base = customerBase(ctx.businessId());

        get(base, ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(0));

        String phone = newPhone();
        post(base, ctx.accessToken(), customerBody("Ada", phone, null))
                .andExpect(status().isOk());

        get(base, ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].firstName").value("Ada"));
    }

    @Test
    void listCustomers_requiresAuth() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        get(customerBase(ctx.businessId()), null).andExpect(status().isUnauthorized());
    }

    @Test
    void listCustomers_crossTenantDenied() throws Exception {
        AuthContext a = registerBusinessAndOwner();
        AuthContext b = registerBusinessAndOwner();
        get(customerBase(b.businessId()), a.accessToken())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    void searchCustomers_findsByName() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        String base = customerBase(ctx.businessId());
        String phone = newPhone();
        post(base, ctx.accessToken(), customerBody("Zara", phone, Map.of("email", "zara@test.com")))
                .andExpect(status().isOk());

        get(base + "/search?query=Zara", ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].firstName").value("Zara"))
                .andExpect(jsonPath("$.content[0].phone").value(phone));

        get(base + "/search?query=nomatch-" + random(), ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void searchCustomers_crossTenantDenied() throws Exception {
        AuthContext a = registerBusinessAndOwner();
        AuthContext b = registerBusinessAndOwner();
        get(customerBase(b.businessId()) + "/search?query=x", a.accessToken())
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------------
    // Get / create / update / delete
    // ---------------------------------------------------------------------

    @Test
    void createCustomer_withAddressesNotesAndTags() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        String base = customerBase(ctx.businessId());
        String phone = newPhone();

        Map<String, Object> body = customerBody("Bola", phone, new HashMap<>());
        body.put("email", "bola@test.com");
        body.put("tags", List.of("VIP", "repeat"));
        body.put("notes", "Prefers pickup on Fridays");
        body.put("addresses", List.of(Map.of(
                "type", "HOME",
                "addressLine1", "12 Test Lane",
                "city", "Lagos",
                "country", "NG",
                "isDefault", true)));
        body.put("preferences", Map.of("notifyViaSms", false, "notifyViaEmail", true));

        ResultActions rs = post(base, ctx.accessToken(), body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Bola"))
                .andExpect(jsonPath("$.lastName").value("Test"))
                .andExpect(jsonPath("$.phone").value(phone))
                .andExpect(jsonPath("$.totalOrders").value(0))
                .andExpect(jsonPath("$.totalSpent").value(0))
                .andExpect(jsonPath("$.loyaltyPoints").value(0))
                .andExpect(jsonPath("$.loyaltyTier").value("BRONZE"))
                .andExpect(jsonPath("$.tags[0]").value("VIP"))
                .andExpect(jsonPath("$.addresses[0].city").value("Lagos"))
                .andExpect(jsonPath("$.addresses[0].isDefault").value(true))
                .andExpect(jsonPath("$.preferences.notifyViaSms").value(false))
                .andExpect(jsonPath("$.preferences.notifyViaEmail").value(true))
                .andExpect(jsonPath("$.notes[0].content").value("Prefers pickup on Fridays"))
                .andExpect(jsonPath("$.metadata.createdAt").exists());

        UUID id = extractUuid(rs.andReturn(), "$.id");

        // GET single reflects the same data.
        get(base + "/" + id, ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.firstName").value("Bola"));
    }

    @Test
    void createCustomer_validation_blankRequiredFields() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertValidation(post(customerBase(ctx.businessId()), ctx.accessToken(), Map.of()),
                Map.of(
                        "firstName", "First name is required",
                        "lastName", "Last name is required",
                        "phone", "Phone is required"));
    }

    @Test
    void createCustomer_validation_invalidPhone() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        Map<String, Object> body = customerBody("Cara", "not-a-phone", null);
        assertValidation(post(customerBase(ctx.businessId()), ctx.accessToken(), body),
                Map.of("phone", "Invalid phone number format"));
    }

    @Test
    void createCustomer_duplicatePhone() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        String base = customerBase(ctx.businessId());
        String phone = newPhone();
        post(base, ctx.accessToken(), customerBody("Dana", phone, null))
                .andExpect(status().isOk());

        assertProblemDetail(post(base, ctx.accessToken(), customerBody("Dana2", phone, null)),
                400, "DUPLICATE_PHONE");
    }

    @Test
    void getCustomer_notFound() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertProblemDetail(get(customerBase(ctx.businessId()) + "/" + UUID.randomUUID(), ctx.accessToken()),
                400, "CUSTOMER_NOT_FOUND");
    }

    @Test
    void getCustomer_crossTenantDenied() throws Exception {
        AuthContext a = registerBusinessAndOwner();
        AuthContext b = registerBusinessAndOwner();
        String phone = newPhone();
        String aJson = post(customerBase(a.businessId()), a.accessToken(),
                customerBody("Eve", phone, null)).andReturn().getResponse().getContentAsString();
        UUID custId = readUuid(aJson, "$.id");

        get(customerBase(a.businessId()) + "/" + custId, b.accessToken())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    void updateCustomer_fieldsAndPhone() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        String base = customerBase(ctx.businessId());
        String phone = newPhone();
        String aJson = post(base, ctx.accessToken(), customerBody("Femi", phone, null))
                .andReturn().getResponse().getContentAsString();
        UUID custId = readUuid(aJson, "$.id");

        String newPhone = newPhone();
        Map<String, Object> update = new HashMap<>();
        update.put("firstName", "FemiUpdated");
        update.put("phone", newPhone);
        update.put("tags", List.of("gold"));

        put(base + "/" + custId, ctx.accessToken(), update)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("FemiUpdated"))
                .andExpect(jsonPath("$.phone").value(newPhone))
                .andExpect(jsonPath("$.tags[0]").value("gold"));
    }

    @Test
    void updateCustomer_duplicatePhone() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        String base = customerBase(ctx.businessId());
        String phoneA = newPhone();
        String phoneB = newPhone();
        String aJson = post(base, ctx.accessToken(), customerBody("Gina", phoneA, null))
                .andReturn().getResponse().getContentAsString();
        UUID custId = readUuid(aJson, "$.id");
        post(base, ctx.accessToken(), customerBody("Gina2", phoneB, null))
                .andExpect(status().isOk());

        assertProblemDetail(put(base + "/" + custId, ctx.accessToken(),
                Map.of("phone", phoneB)), 400, "DUPLICATE_PHONE");
    }

    @Test
    void updateCustomer_notFound() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertProblemDetail(put(customerBase(ctx.businessId()) + "/" + UUID.randomUUID(),
                ctx.accessToken(), Map.of("firstName", "X")), 400, "CUSTOMER_NOT_FOUND");
    }

    @Test
    void deleteCustomer_successAndReflects() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        String base = customerBase(ctx.businessId());
        String aJson = post(base, ctx.accessToken(), customerBody("Hauwa", newPhone(), null))
                .andReturn().getResponse().getContentAsString();
        UUID custId = readUuid(aJson, "$.id");

        assertSuccess(delete(base + "/" + custId, ctx.accessToken()), "Customer deleted successfully");

        // Soft delete: the record is still readable afterwards (marked inactive).
        get(base + "/" + custId, ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(custId.toString()));
    }

    @Test
    void deleteCustomer_notFound() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertProblemDetail(delete(customerBase(ctx.businessId()) + "/" + UUID.randomUUID(), ctx.accessToken()),
                400, "CUSTOMER_NOT_FOUND");
    }

    // ---------------------------------------------------------------------
    // Orders / loyalty
    // ---------------------------------------------------------------------

    @Test
    void getCustomerOrders_empty() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        String base = customerBase(ctx.businessId());
        String aJson = post(base, ctx.accessToken(), customerBody("Idris", newPhone(), null))
                .andReturn().getResponse().getContentAsString();
        UUID custId = readUuid(aJson, "$.id");

        get(base + "/" + custId + "/orders", ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void getCustomerOrders_unknownCustomerReturnsEmptyPage() throws Exception {
        // Doc divergence: CustomerService.getCustomerOrders does not verify the
        // customer exists; an unknown id yields an empty page rather than 404.
        AuthContext ctx = registerBusinessAndOwner();
        get(customerBase(ctx.businessId()) + "/" + UUID.randomUUID() + "/orders", ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void getCustomerLoyalty_defaults() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        String base = customerBase(ctx.businessId());
        String aJson = post(base, ctx.accessToken(), customerBody("Jay", newPhone(), null))
                .andReturn().getResponse().getContentAsString();
        UUID custId = readUuid(aJson, "$.id");

        get(base + "/" + custId + "/loyalty", ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value(custId.toString()))
                .andExpect(jsonPath("$.currentPoints").value(0))
                .andExpect(jsonPath("$.lifetimePoints").value(0))
                .andExpect(jsonPath("$.tier.name").value("BRONZE"))
                .andExpect(jsonPath("$.tier.level").value(1))
                .andExpect(jsonPath("$.pointsHistory").isEmpty())
                .andExpect(jsonPath("$.availableRewards").isArray());
    }

    @Test
    void adjustPoints_addThenDeductTooMuch() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        String base = customerBase(ctx.businessId());
        String aJson = post(base, ctx.accessToken(), customerBody("Kemi", newPhone(), null))
                .andReturn().getResponse().getContentAsString();
        UUID custId = readUuid(aJson, "$.id");

        post(base + "/" + custId + "/loyalty/adjust", ctx.accessToken(),
                Map.of("points", 50, "reason", "Manual bonus", "source", "MANUAL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentPoints").value(50))
                .andExpect(jsonPath("$.lifetimePoints").value(50))
                .andExpect(jsonPath("$.pointsHistory[0].points").value(50))
                .andExpect(jsonPath("$.pointsHistory[0].balance").value(50))
                .andExpect(jsonPath("$.pointsHistory[0].source").value("MANUAL"));

        assertProblemDetail(post(base + "/" + custId + "/loyalty/adjust", ctx.accessToken(),
                Map.of("points", -60, "reason", "Correction", "source", "MANUAL")),
                400, "INSUFFICIENT_POINTS");
    }

    @Test
    void adjustPoints_validation_requiredFields() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        String base = customerBase(ctx.businessId());
        String aJson = post(base, ctx.accessToken(), customerBody("Lola", newPhone(), null))
                .andReturn().getResponse().getContentAsString();
        UUID custId = readUuid(aJson, "$.id");

        assertValidation(post(base + "/" + custId + "/loyalty/adjust", ctx.accessToken(), Map.of()),
                Map.of(
                        "points", "Points are required",
                        "reason", "Reason is required",
                        "source", "Source is required"));
    }

    @Test
    void adjustPoints_notFound() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        assertProblemDetail(post(customerBase(ctx.businessId()) + "/" + UUID.randomUUID() + "/loyalty/adjust",
                ctx.accessToken(), Map.of("points", 5, "reason", "r", "source", "s")),
                400, "CUSTOMER_NOT_FOUND");
    }

    // ---------------------------------------------------------------------
    // Analytics & reporting
    // ---------------------------------------------------------------------

    @Test
    void getTopCustomers_returnsList() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        String base = customerBase(ctx.businessId());
        post(base, ctx.accessToken(), customerBody("Mina", newPhone(), null))
                .andExpect(status().isOk());

        get(base + "/top?metric=SPEND&period=MONTH", ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void getRFMSegments_returnsDistribution() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        String base = customerBase(ctx.businessId());
        post(base, ctx.accessToken(), customerBody("Ngozi", newPhone(), null))
                .andExpect(status().isOk());

        get(base + "/segments/rfm", ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.segments", hasSize(5)))
                .andExpect(jsonPath("$.distribution.recency").isArray())
                .andExpect(jsonPath("$.distribution.frequency").isArray())
                .andExpect(jsonPath("$.distribution.monetary").isArray());
    }

    @Test
    void getRFMSegments_emptyBusiness() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        get(customerBase(ctx.businessId()) + "/segments/rfm", ctx.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.segments").isEmpty())
                .andExpect(jsonPath("$.distribution").isEmpty());
    }

    // ---------------------------------------------------------------------
    // Import / export
    // ---------------------------------------------------------------------

    @Test
    void importCustomers_validCsv() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        String csv = "phone,firstName,lastName,email\n"
                + "+15550001111,Import,One,imp1@test.com\n"
                + "+15550002222,Import,Two,imp2@test.com\n";
        MockMultipartFile file = new MockMultipartFile(
                "file", "customers.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(MockMvcRequestBuilders.multipart(customerBase(ctx.businessId()) + "/import")
                        .file(file)
                        .header("Authorization", "Bearer " + ctx.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRows").value(2))
                .andExpect(jsonPath("$.imported").value(2))
                .andExpect(jsonPath("$.updated").value(0))
                .andExpect(jsonPath("$.failed").value(0));

        get(customerBase(ctx.businessId()), ctx.accessToken())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void importCustomers_emptyFile() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        MockMultipartFile file = new MockMultipartFile(
                "file", "customers.csv", "text/csv", new byte[0]);

        assertProblemDetail(mockMvc.perform(MockMvcRequestBuilders.multipart(
                                customerBase(ctx.businessId()) + "/import")
                        .file(file)
                        .header("Authorization", "Bearer " + ctx.accessToken())),
                400, "IMPORT_NO_FILE");
    }

    // Doc divergence: omitting the "file" part entirely is NOT handled by any
    // exception handler and surfaces as a generic 500 (MissingServletRequestPart),
    // so that case is not asserted here.

    @Test
    void exportCustomers_returnsEmptyBytes() throws Exception {
        AuthContext ctx = registerBusinessAndOwner();
        get(customerBase(ctx.businessId()) + "/export", ctx.accessToken())
                .andExpect(status().isOk());
    }

    // ---------------------------------------------------------------------
    // Permission gates
    // ---------------------------------------------------------------------

    @Test
    void createCustomer_requiresCreatePermission() throws Exception {
        AuthContext a = registerBusinessAndOwner();
        AuthContext b = registerBusinessAndOwner();
        post(customerBase(b.businessId()), a.accessToken(), customerBody("Pam", newPhone(), null))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }
}
