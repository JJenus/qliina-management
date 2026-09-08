package com.jjenus.qliina_management.integration;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Verifies the whole-backend rate limiter: a per-IP strict budget on
 * /api/v1/auth/** (login brute-force guard) returns 429 ProblemDetail once
 * exhausted. Bucket budgets are tiny in this isolated context so three
 * requests are enough; the shared BaseIntegrationTest contexts keep the
 * generous test-profile defaults.
 */
@TestPropertySource(properties = {
        "app.rate-limit.default-limit.capacity=1000",
        "app.rate-limit.default-limit.refill-per-minute=600",
        "app.rate-limit.login.capacity=2",
        "app.rate-limit.login.refill-per-minute=1",
})
class RateLimitIntegrationTest extends BaseIntegrationTest {

    @Test
    void loginIsThrottledWith429AfterPerIpBudgetExhausted() throws Exception {
        Map<String, Object> creds = Map.of(
                "username", "ghost_user",
                "password", "WrongPassw0rd!");

        mockMvc.perform(MockMvcRequestBuilders
                        .post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(creds)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"));

        mockMvc.perform(MockMvcRequestBuilders
                        .post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(creds)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"));

        mockMvc.perform(MockMvcRequestBuilders
                        .post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(creds)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.title").value("Too Many Requests"))
                .andExpect(jsonPath("$.errorCode").value("RATE_LIMITED"));
    }
}