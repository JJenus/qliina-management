package com.jjenus.qliina_management.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.jjenus.qliina_management.employee.service.IdleDetectionService;
import com.jjenus.qliina_management.employee.service.MidnightAutoCloseService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base class for all integration tests.
 *
 * Runs against the full Spring context (H2, test profile), authenticating with real
 * JWTs obtained through the login / register-business endpoints. Scheduled jobs that
 * could interfere with shift tests are mocked out.
 */
@Slf4j
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class BaseIntegrationTest {

    public static final String DEFAULT_PASSWORD = "Passw0rd!";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @MockitoBean
    protected IdleDetectionService idleDetectionService;

    @MockitoBean
    protected MidnightAutoCloseService midnightAutoCloseService;

    protected static final AtomicLong counter = new AtomicLong();

    // ---------------------------------------------------------------------
    // Auth / bootstrap helpers
    // ---------------------------------------------------------------------

    /** Auth context for a freshly registered business (BUSINESS_ADMIN user). */
    protected AuthContext registerBusinessAndOwner() throws Exception {
        return registerBusinessAndOwner(null);
    }

    protected AuthContext registerBusinessAndOwner(String explicitUsername) throws Exception {
        String unique = random();
        String username = explicitUsername != null ? explicitUsername : "owner_" + unique;
        long n = counter.incrementAndGet();
        String phone = "+1" + (555_000_0000L + n);
        String email = username + "@test.com";

        Map<String, Object> body = new HashMap<>();
        body.put("businessName", "Test Business " + unique);
        body.put("slug", "biz_" + unique);
        body.put("businessEmail", "biz_" + unique + "@test.com");
        body.put("businessPhone", phone);
        body.put("shopName", "Main Shop");
        body.put("shopCode", "SH" + unique.substring(0, 4).toUpperCase());
        body.put("firstName", "Owner");
        body.put("lastName", "One");
        body.put("username", username);
        body.put("email", email);
        body.put("phone", phone);
        body.put("password", DEFAULT_PASSWORD);
        body.put("confirmPassword", DEFAULT_PASSWORD);

        MvcResult res = mockMvc.perform(MockMvcRequestBuilders
                        .post("/api/v1/auth/register-business")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(body)))
                .andExpect(status().isCreated())
                .andReturn();

        String json = res.getResponse().getContentAsString(StandardCharsets.UTF_8);
        return new AuthContext(
                readString(json, "$.accessToken"),
                readUuid(json, "$.businessId"),
                readUuid(json, "$.shopId"),
                readUuid(json, "$.user.id"),
                username,
                DEFAULT_PASSWORD,
                email,
                phone);
    }

    /** Performs a real login and returns the access token. */
    protected String loginToken(String username, String password) throws Exception {
        MvcResult res = mockMvc.perform(MockMvcRequestBuilders
                        .post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of(
                                "username", username,
                                "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        return readString(res.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.accessToken");
    }

    protected String adminToken() throws Exception {
        return loginToken("admin", "Admin@123");
    }

    protected String random() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    // ---------------------------------------------------------------------
    // Request helpers
    // ---------------------------------------------------------------------

    protected ResultActions request(HttpMethod method, String path, String token, Object body) throws Exception {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.request(method, path);
        if (body != null) {
            builder.content(objectMapper.writeValueAsBytes(body))
                    .contentType(MediaType.APPLICATION_JSON);
        }
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return mockMvc.perform(builder);
    }

    protected ResultActions get(String path, String token) throws Exception {
        return request(HttpMethod.GET, path, token, null);
    }

    protected ResultActions post(String path, String token, Object body) throws Exception {
        return request(HttpMethod.POST, path, token, body);
    }

    protected ResultActions put(String path, String token, Object body) throws Exception {
        return request(HttpMethod.PUT, path, token, body);
    }

    protected ResultActions patch(String path, String token, Object body) throws Exception {
        return request(HttpMethod.PATCH, path, token, body);
    }

    protected ResultActions delete(String path, String token) throws Exception {
        return request(HttpMethod.DELETE, path, token, null);
    }

    protected ResultActions delete(String path, String token, Object body) throws Exception {
        return request(HttpMethod.DELETE, path, token, body);
    }

    // ---------------------------------------------------------------------
    // Response assertions
    // ---------------------------------------------------------------------

    protected void assertProblemDetail(ResultActions rs, int statusCode, String errorCode) throws Exception {
        rs.andExpect(status().is(statusCode))
                .andExpect(jsonPath("$.status").value(statusCode))
                .andExpect(jsonPath("$.errorCode").value(errorCode))
                .andExpect(jsonPath("$.path").exists());
    }

    protected void assertProblemDetail(ResultActions rs, int statusCode, String title, String errorCode) throws Exception {
        rs.andExpect(status().is(statusCode))
                .andExpect(jsonPath("$.status").value(statusCode))
                .andExpect(jsonPath("$.title").value(title))
                .andExpect(jsonPath("$.errorCode").value(errorCode))
                .andExpect(jsonPath("$.path").exists());
    }

    /** Asserts a validation-error ProblemDetail body with the given field->message map. */
    protected void assertValidation(ResultActions rs, Map<String, String> expected) throws Exception {
        rs.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation Error"))
                .andExpect(jsonPath("$.status").value(400));
        for (Map.Entry<String, String> e : expected.entrySet()) {
            rs.andExpect(jsonPath("$.errors." + e.getKey()).value(e.getValue()));
        }
    }

    protected void assertSuccess(ResultActions rs, String message) throws Exception {
        rs.andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value(message))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    protected void assertPage(ResultActions rs) throws Exception {
        rs.andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.pageNumber").isNumber())
                .andExpect(jsonPath("$.pageSize").isNumber())
                .andExpect(jsonPath("$.totalElements").isNumber())
                .andExpect(jsonPath("$.totalPages").isNumber());
    }

    // ---------------------------------------------------------------------
    // JSON extraction helpers
    // ---------------------------------------------------------------------

    protected UUID readUuid(String json, String path) throws Exception {
        Object v = JsonPath.read(json, path);
        if (v == null) return null;
        return UUID.fromString(v.toString());
    }

    protected String readString(String json, String path) throws Exception {
        Object v = JsonPath.read(json, path);
        return v == null ? null : v.toString();
    }

    protected int readInt(String json, String path) throws Exception {
        Object v = JsonPath.read(json, path);
        return v == null ? -1 : Integer.parseInt(v.toString());
    }

    protected long readLong(String json, String path) throws Exception {
        Object v = JsonPath.read(json, path);
        return v == null ? -1L : Long.parseLong(v.toString());
    }

    protected double readDouble(String json, String path) throws Exception {
        Object v = JsonPath.read(json, path);
        return v == null ? -1d : Double.parseDouble(v.toString());
    }

    protected UUID extractUuid(MvcResult res, String path) throws Exception {
        return readUuid(res.getResponse().getContentAsString(StandardCharsets.UTF_8), path);
    }

    protected String extractString(MvcResult res, String path) throws Exception {
        return readString(res.getResponse().getContentAsString(StandardCharsets.UTF_8), path);
    }

    protected int extractInt(MvcResult res, String path) throws Exception {
        return readInt(res.getResponse().getContentAsString(StandardCharsets.UTF_8), path);
    }

    protected long extractLong(MvcResult res, String path) throws Exception {
        return readLong(res.getResponse().getContentAsString(StandardCharsets.UTF_8), path);
    }

    /** Serializes to JSON string. */
    protected String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    /** Auth context returned by registerBusinessAndOwner(). */
    public record AuthContext(
            String accessToken,
            UUID businessId,
            UUID shopId,
            UUID userId,
            String username,
            String password,
            String email,
            String phone) {
    }
}
