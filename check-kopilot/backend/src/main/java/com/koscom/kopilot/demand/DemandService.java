package com.koscom.kopilot.demand;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 카탈로그 밖 질문의 수요를 적재한다.
 *  - AUTO     : 가이드 카드가 뜬 순간(= 카탈로그가 못 답한 질문) 자동 기록
 *  - EXPLICIT : 사용자가 "카탈로그 추가 요청" 버튼을 눌러 의사를 명시한 경우
 * 두 신호를 분리해 두면 Admin에서 "수요량"과 "강도"를 구분해 지표 확장 우선순위를 뽑을 수 있다.
 */
@Service
public class DemandService implements DemandRecorder {

    private static final int TOPIC_MAX = 255;

    private final JdbcTemplate jdbc;

    public DemandService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public void record(String sessionId, String topic, String matchedApiIds, String source) {
        if (topic == null || topic.isBlank()) return;
        String normalized = topic.trim().replaceAll("\\s+", " ");
        if (normalized.length() > TOPIC_MAX) normalized = normalized.substring(0, TOPIC_MAX);
        String apiIds = (matchedApiIds == null || matchedApiIds.isBlank()) ? null
                : (matchedApiIds.length() > 255 ? matchedApiIds.substring(0, 255) : matchedApiIds);
        try {
            jdbc.update("""
                INSERT INTO catalog_request(session_id, topic, matched_api_ids, source)
                VALUES (?, ?, ?, ?)
                """, sessionId, normalized, apiIds, source);
        } catch (RuntimeException e) {
            // 수요 적재 실패가 사용자 답변을 막아서는 안 된다
        }
    }
}
