package com.koscom.kopilot.demand;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("fixture")
@TestPropertySource(properties = "admin.token=test-token")
class AdminControllerTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        jdbc.update("DELETE FROM catalog_request WHERE session_id LIKE 'admin-test-%'");
        jdbc.update("INSERT INTO catalog_request(session_id, topic, matched_api_ids, source) VALUES (?,?,?,?)",
                "admin-test-1", "외국인 수급", "stock-investor", "AUTO");
        jdbc.update("INSERT INTO catalog_request(session_id, topic, matched_api_ids, source) VALUES (?,?,?,?)",
                "admin-test-2", "외국인 수급", "stock-investor", "EXPLICIT");
    }

    @Test
    void summaryAggregatesByTopic_whenTokenValid() throws Exception {
        mvc.perform(get("/api/admin/demand/summary").header("X-Admin-Token", "test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].topic").value("외국인 수급"))
                .andExpect(jsonPath("$[0].requestCount").value(2))
                .andExpect(jsonPath("$[0].explicitCount").value(1))
                .andExpect(jsonPath("$[0].sessionCount").value(2));
    }

    @Test
    void acceptsQueryParamToken_forBrowserLinks() throws Exception {
        mvc.perform(get("/api/admin/demand/summary").param("token", "test-token"))
                .andExpect(status().isOk());
    }

    @Test
    void hidesEndpointWithNotFound_whenTokenMissingOrWrong() throws Exception {
        mvc.perform(get("/api/admin/demand/summary")).andExpect(status().isNotFound());
        mvc.perform(get("/api/admin/demand/summary").header("X-Admin-Token", "nope"))
                .andExpect(status().isNotFound());
    }
}
