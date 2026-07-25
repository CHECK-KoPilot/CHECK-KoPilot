package com.koscom.kopilot.export;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.koscom.kopilot.domain.MetricResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class CardStore {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    public CardStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void save(String sessionId, MetricResult r) {
        try {
            // MySQL card.id는 CHAR(36) — UUID 형식만 허용하고 문자열로 바인딩한다
            jdbc.update("INSERT INTO card(id, session_id, payload) VALUES (?,?,?)",
                    UUID.fromString(r.cardId()).toString(), sessionId, mapper.writeValueAsString(r));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("카드 직렬화 실패", e);
        }
    }

    public Optional<MetricResult> find(String cardId) {
        String id;
        try { id = UUID.fromString(cardId).toString(); }
        catch (IllegalArgumentException e) { return Optional.empty(); }   // 잘못된 cardId → 404
        var list = jdbc.queryForList("SELECT payload FROM card WHERE id = ?", String.class, id);
        return list.stream().findFirst().map(json -> {
            try { return mapper.readValue(json, MetricResult.class); }
            catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                throw new IllegalStateException("카드 역직렬화 실패", e);
            }
        });
    }

    /** 직렬화에 쓰는 mapper — ChatService의 카드 SSE 이벤트에도 동일 mapper 사용 */
    public ObjectMapper mapper() { return mapper; }
}
