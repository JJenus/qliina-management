package com.jjenus.qliina_management.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the dev-only test-user endpoint used by the login page's one-click
 * buttons. This context keeps {@code app.seed-demo.enabled=true} (the default
 * for the test profile) so the "qliina-demo" tenant and its users exist.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DevTestUsersIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsEverySeededDemoUserWithPasswordAndRole() throws Exception {
        mockMvc.perform(get("/api/v1/public/dev/test-users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(7))
                .andExpect(jsonPath("$[?(@.username=='owner')].roles[0]").value("BUSINESS_ADMIN"))
                .andExpect(jsonPath("$[?(@.username=='manager')].roles[0]").value("SHOP_MANAGER"))
                .andExpect(jsonPath("$[?(@.username=='frontdesk')].roles[0]").value("FRONT_DESK"))
                .andExpect(jsonPath("$[?(@.username=='washer1')].roles[0]").value("WASHER"))
                .andExpect(jsonPath("$[?(@.username=='washer2')].roles[0]").value("WASHER"))
                .andExpect(jsonPath("$[?(@.username=='ironer1')].roles[0]").value("IRONER"))
                .andExpect(jsonPath("$[?(@.username=='delivery1')].roles[0]").value("DELIVERY"))
                .andExpect(jsonPath("$[?(@.username=='manager')].password").value("Passw0rd!"))
                .andExpect(jsonPath("$[?(@.username=='manager')].displayName").value("Sarah Manager"));
    }
}