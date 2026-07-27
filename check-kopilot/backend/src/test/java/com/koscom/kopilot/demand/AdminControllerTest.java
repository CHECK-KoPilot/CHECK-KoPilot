package com.koscom.kopilot.demand;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("fixture")
@TestPropertySource(properties = "admin.token=test-token")
class AdminControllerTest {

    /**
     * 개발용 MySQL은 로컬 실행·데모로 쌓인 수요 데이터를 그대로 들고 있고, summary는 테이블 전체를
     * 주제별로 집계한다. 그래서 이 테스트는 "집계 결과의 첫 항목"을 단정할 수 없다 —
     * 데모 주제가 더 많이 쌓이면 순위가 밀린다(이슈 #64). 아무도 쓰지 않을 주제를 심고
     * 그 주제만 찾아 검증한다.
     */
    private static final String TOPIC = "admin-test 외국인 수급";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        jdbc.update("DELETE FROM catalog_request WHERE topic = ?", TOPIC);
        jdbc.update("INSERT INTO catalog_request(session_id, topic, matched_api_ids, source) VALUES (?,?,?,?)",
                "admin-test-1", TOPIC, "stock-investor", "AUTO");
        jdbc.update("INSERT INTO catalog_request(session_id, topic, matched_api_ids, source) VALUES (?,?,?,?)",
                "admin-test-2", TOPIC, "stock-investor", "EXPLICIT");
    }

    @Test
    void summaryAggregatesByTopic_whenTokenValid() throws Exception {
        // limit은 상한(200)까지 올린다 — 개발 DB에 주제가 쌓여 있어도 심어둔 행이 잘리지 않게
        String body = mvc.perform(get("/api/admin/demand/summary")
                        .param("limit", "200")
                        .header("X-Admin-Token", "test-token"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        JsonNode row = StreamSupport.stream(new ObjectMapper().readTree(body).spliterator(), false)
                .filter(n -> TOPIC.equals(n.path("topic").asText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("심어둔 주제가 집계에 없다: " + body));

        assertThat(row.path("requestCount").asInt()).isEqualTo(2);
        assertThat(row.path("explicitCount").asInt()).isEqualTo(1);   // EXPLICIT 1건만 센다
        assertThat(row.path("sessionCount").asInt()).isEqualTo(2);    // 서로 다른 세션 2개
        assertThat(row.path("matchedApiIds").asText()).contains("stock-investor");
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
