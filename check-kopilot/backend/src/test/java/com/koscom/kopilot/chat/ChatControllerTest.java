package com.koscom.kopilot.chat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 요청 계약(docs/api.md) 검증 — LLM은 호출하지 않는 경로만 다룬다. */
@SpringBootTest
@AutoConfigureMockMvc
class ChatControllerTest {

    private static final String SESSION = "11111111-1111-4111-8111-111111111111";

    @Autowired
    private MockMvc mvc;

    @MockBean
    private ConversationStore conversations;

    @Test
    void deleteSession_clearsServerSideContext() throws Exception {
        mvc.perform(delete("/api/chat/" + SESSION))
                .andExpect(status().isNoContent());

        // "새 대화"가 클라이언트 UUID만 바꾸면 서버 컨텍스트는 TTL 2시간 동안 남는다
        verify(conversations).clear(SESSION);
    }

    @Test
    void deleteSession_rejectsMalformedSessionId() throws Exception {
        mvc.perform(delete("/api/chat/bad!id"))
                .andExpect(status().isBadRequest());

        verify(conversations, never()).clear(anyString());
    }

    @Test
    void chat_rejectsBlankMessage() throws Exception {
        mvc.perform(post("/api/chat/" + SESSION)
                        .contentType("application/json")
                        .content("{\"message\":\"  \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void chat_rejectsMissingMessage() throws Exception {
        mvc.perform(post("/api/chat/" + SESSION)
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void chat_rejectsOverlongMessage() throws Exception {
        mvc.perform(post("/api/chat/" + SESSION)
                        .contentType("application/json")
                        .content("{\"message\":\"" + "가".repeat(2_001) + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void chat_rejectsMalformedSessionId() throws Exception {
        mvc.perform(post("/api/chat/bad!id")
                        .contentType("application/json")
                        .content("{\"message\":\"질문\"}"))
                .andExpect(status().isBadRequest());
    }
}
