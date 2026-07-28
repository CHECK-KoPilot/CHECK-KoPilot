package com.koscom.kopilot.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("fixture")
class CatalogControllerTest {

    @Autowired MockMvc mvc;
    @Autowired CatalogService catalog;

    @Test
    void listsEveryExecutorWithPresetMeta() throws Exception {
        String body = mvc.perform(get("/api/catalog"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        JsonNode items = new ObjectMapper().readTree(body);
        assertThat(items.size()).isEqualTo(catalog.all().size());

        // 라벨·템플릿이 비면 단축키 폼이 빈 드롭다운을 그린다 — 계약으로 못 박는다
        for (JsonNode item : items) {
            assertThat(item.path("label").asText()).isNotBlank();
            assertThat(item.path("promptTemplate").asText()).contains("{targets}");
            assertThat(item.path("minTargets").asInt()).isGreaterThanOrEqualTo(1);
            assertThat(item.path("maxTargets").asInt())
                    .isGreaterThanOrEqualTo(item.path("minTargets").asInt());
        }
    }

    @Test
    void returnGapTakesExactlyTwoTargets() throws Exception {
        String body = mvc.perform(get("/api/catalog"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        JsonNode gap = StreamSupport.stream(new ObjectMapper().readTree(body).spliterator(), false)
                .filter(n -> "return_gap".equals(n.path("toolName").asText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("return_gap이 카탈로그에 없다: " + body));

        assertThat(gap.path("minTargets").asInt()).isEqualTo(2);
        assertThat(gap.path("maxTargets").asInt()).isEqualTo(2);
    }
}
