package com.koscom.kopilot.checkapi;

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
class StockSearchControllerTest {

    @Autowired MockMvc mvc;

    private JsonNode search(String query, String limit) throws Exception {
        String body = mvc.perform(get("/api/stocks").param("q", query).param("limit", limit))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return new ObjectMapper().readTree(body);
    }

    @Test
    void partialName_returnsCandidatesWithCode() throws Exception {
        JsonNode results = search("삼성", "8");

        assertThat(StreamSupport.stream(results.spliterator(), false)
                .anyMatch(n -> "005930".equals(n.path("code").asText()))).isTrue();
        assertThat(results.get(0).path("name").asText()).isNotBlank();
        assertThat(results.get(0).path("market").asText()).isNotBlank();
    }

    @Test
    void limitIsRespected() throws Exception {
        assertThat(search("삼성", "3").size()).isLessThanOrEqualTo(3);
    }

    /** 1자 질의는 마스터 4천 행을 통째로 훑게 만든다 — 자동완성이 시작되기 전에 잘라낸다. */
    @Test
    void singleCharQuery_returnsEmpty() throws Exception {
        assertThat(search("삼", "8").size()).isZero();
    }
}
