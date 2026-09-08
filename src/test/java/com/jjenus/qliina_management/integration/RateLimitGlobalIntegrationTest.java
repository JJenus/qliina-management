package com.jjenus.qliina_management.integration;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Verifies the global (whole-backend) rate-limit budget on every /api/** request,
 * independent of the stricter login budget. Global capacity is 1 and the login
 * budget is huge, so only the global path can produce the 429: the first request
 * consumes the single global token and reaches auth (401); the second is rejected
 * before auth with RATE_LIMITED.
 */
@TestPropertySource(properties = {
        "app.rate-limit.default-limit.capacity=1",
        "app.rate-limit.default-limit.refill-per-minute=1",
        "app.rate-limit.login.capacity=10000",
        "app.rate-limit.login.refill-per-minute=60000",
})
class RateLimitGlobalIntegrationTest extends BaseIntegrationTest {

    @Test
    void globalBudgetReturns429RightAfterSingleTokenIsConsumed() throws Exception {
        Map<String, Object> creds = Map.of(
                "username", "ghost_user_global",
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
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.title").value("Too Many Requests"))
                .andExpect(jsonPath("$.errorCode").value("RATE_LIMITED"))
                .andExpect(jsonPath("$.path").value("/api/v1/auth/login"));
    }
}