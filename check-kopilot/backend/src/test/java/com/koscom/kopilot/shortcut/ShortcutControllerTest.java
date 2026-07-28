package com.koscom.kopilot.shortcut;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("fixture")
class ShortcutControllerTest {

    private static final String DEVICE = "test-device-ctl-A";
    private static final String OTHER_DEVICE = "test-device-ctl-B";

    private static final String VALID_BODY = """
            {"keyCombo":"ctrl+shift+1","toolName":"return_gap",
             "targets":["삼성전자(005930)","SK하이닉스(000660)"],"period":"3M",
             "prompt":"삼성전자와 SK하이닉스의 최근 3개월 수익률 갭을 비교해줘"}
            """;

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM shortcut WHERE device_id IN (?, ?)", DEVICE, OTHER_DEVICE);
    }

    private String create(String device, String body) throws Exception {
        return mvc.perform(post("/api/shortcuts")
                        .header("X-Device-Id", device)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void created_thenListedForSameDevice() throws Exception {
        JsonNode created = new ObjectMapper().readTree(create(DEVICE, VALID_BODY));
        assertThat(created.path("id").asText()).isNotBlank();
        assertThat(created.path("targets").size()).isEqualTo(2);

        String list = mvc.perform(get("/api/shortcuts").header("X-Device-Id", DEVICE))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        JsonNode items = new ObjectMapper().readTree(list);
        assertThat(items.size()).isEqualTo(1);
        assertThat(items.get(0).path("prompt").asText()).contains("수익률 갭");
        assertThat(items.get(0).path("targets").get(0).asText()).isEqualTo("삼성전자(005930)");
    }

    @Test
    void otherDevice_seesNothing() throws Exception {
        create(DEVICE, VALID_BODY);

        mvc.perform(get("/api/shortcuts").header("X-Device-Id", OTHER_DEVICE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void duplicateCombo_conflicts() throws Exception {
        create(DEVICE, VALID_BODY);

        mvc.perform(post("/api/shortcuts")
                        .header("X-Device-Id", DEVICE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("KEY_TAKEN"));
    }

    @Test
    void badKeyCombo_isRejected() throws Exception {
        mvc.perform(post("/api/shortcuts")
                        .header("X-Device-Id", DEVICE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY.replace("ctrl+shift+1", "ctrl+t")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("KEY_COMBO_INVALID"));
    }

    @Test
    void unknownTool_isRejected() throws Exception {
        mvc.perform(post("/api/shortcuts")
                        .header("X-Device-Id", DEVICE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY.replace("return_gap", "moon_phase")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TOOL_UNKNOWN"));
    }

    /** return_gap은 정확히 2개다. 1개로 저장되면 단축키가 매번 되묻기로 샌다. */
    @Test
    void targetCountOutsideCatalogRange_isRejected() throws Exception {
        mvc.perform(post("/api/shortcuts")
                        .header("X-Device-Id", DEVICE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY.replace("[\"삼성전자(005930)\",\"SK하이닉스(000660)\"]",
                                "[\"삼성전자(005930)\"]")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TARGET_COUNT_INVALID"));
    }

    @Test
    void blankPrompt_isRejected() throws Exception {
        mvc.perform(post("/api/shortcuts")
                        .header("X-Device-Id", DEVICE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY.replace(
                                "삼성전자와 SK하이닉스의 최근 3개월 수익률 갭을 비교해줘", "   ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PROMPT_INVALID"));
    }

    @Test
    void update_replacesFields() throws Exception {
        String id = new ObjectMapper().readTree(create(DEVICE, VALID_BODY)).path("id").asText();

        mvc.perform(put("/api/shortcuts/" + id)
                        .header("X-Device-Id", DEVICE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"keyCombo":"ctrl+shift+9","toolName":"volatility",
                                 "targets":["삼성전자(005930)"],"period":"1M",
                                 "prompt":"삼성전자의 최근 1개월 변동성을 계산해줘"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keyCombo").value("ctrl+shift+9"))
                .andExpect(jsonPath("$.toolName").value("volatility"));
    }

    @Test
    void updatingAnotherDeviceShortcut_isNotFound() throws Exception {
        String id = new ObjectMapper().readTree(create(DEVICE, VALID_BODY)).path("id").asText();

        mvc.perform(put("/api/shortcuts/" + id)
                        .header("X-Device-Id", OTHER_DEVICE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_removesIt() throws Exception {
        String id = new ObjectMapper().readTree(create(DEVICE, VALID_BODY)).path("id").asText();

        mvc.perform(delete("/api/shortcuts/" + id).header("X-Device-Id", DEVICE))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/shortcuts").header("X-Device-Id", DEVICE))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void deletingUnknownId_isNotFound() throws Exception {
        mvc.perform(delete("/api/shortcuts/does-not-exist").header("X-Device-Id", DEVICE))
                .andExpect(status().isNotFound());
    }

    @Test
    void missingDeviceHeader_isBadRequest() throws Exception {
        mvc.perform(get("/api/shortcuts")).andExpect(status().isBadRequest());
    }

    /**
     * 종목명이 긴 ETN을 최대 개수(수익률 순위 = 10개)만큼 담으면 targets가 VARCHAR(255)를 넘긴다.
     * 막지 않으면 INSERT가 터져 500이 나가고, 폼을 다 채운 사용자는 이유를 알 수 없다.
     */
    @Test
    void targetsLongerThanColumn_isRejectedNotCrashed() throws Exception {
        String longName = "하나 Solactive 2X US Tech Top 10 ETN(H)(700023)";
        String tenLongNames = java.util.stream.IntStream.range(0, 10)
                .mapToObj(i -> "\"" + longName + "\"")
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
        String body = VALID_BODY
                .replace("return_gap", "return_ranking")
                .replace("[\"삼성전자(005930)\",\"SK하이닉스(000660)\"]", tenLongNames);

        mvc.perform(post("/api/shortcuts")
                        .header("X-Device-Id", DEVICE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TARGETS_TOO_LONG"));
    }

    /** 기간은 화면이 주는 4종뿐이다. 임의 문자열이 저장되면 실행기가 파싱에서 터진다. */
    @Test
    void unknownPeriod_isRejected() throws Exception {
        mvc.perform(post("/api/shortcuts")
                        .header("X-Device-Id", DEVICE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY.replace("\"3M\"", "\"2Y\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PERIOD_INVALID"));
    }
}
