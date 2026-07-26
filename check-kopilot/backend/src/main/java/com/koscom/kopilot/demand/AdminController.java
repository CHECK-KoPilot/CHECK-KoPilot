package com.koscom.kopilot.demand;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 수요조사 조회 화면용 집계 API.
 * 식별 가능 정보를 노출하지 않기 위해 세션 ID·질문 원문은 반환하지 않고 집계값만 돌려준다.
 */
@RestController
public class AdminController {

    private final JdbcTemplate jdbc;

    public AdminController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping("/api/admin/demand/summary")
    public List<DemandSummary> summary(@RequestParam(defaultValue = "50") int limit) {
        int capped = Math.max(1, Math.min(limit, 200));
        return jdbc.query("""
            SELECT topic,
                   COUNT(*)                                              AS request_count,
                   SUM(CASE WHEN source = 'EXPLICIT' THEN 1 ELSE 0 END)  AS explicit_count,
                   COUNT(DISTINCT session_id)                            AS session_count,
                   SUBSTRING_INDEX(GROUP_CONCAT(COALESCE(matched_api_ids, '')
                                   ORDER BY created_at DESC SEPARATOR '||'), '||', 1) AS matched_api_ids,
                   MAX(created_at)                                       AS last_at
              FROM catalog_request
             GROUP BY topic
             ORDER BY request_count DESC, last_at DESC
             LIMIT ?
            """, (rs, i) -> new DemandSummary(
                        rs.getString("topic"),
                        rs.getLong("request_count"),
                        rs.getLong("explicit_count"),
                        rs.getLong("session_count"),
                        rs.getString("matched_api_ids"),
                        String.valueOf(rs.getTimestamp("last_at"))),
                capped);
    }

    @GetMapping("/api/admin/stats")
    public Map<String, Object> stats() {
        long questions = count("SELECT COUNT(*) FROM chat_log WHERE role = 'user'");
        long cards = count("SELECT COUNT(*) FROM card");
        long guides = count("SELECT COUNT(*) FROM catalog_request WHERE source = 'AUTO'");
        double coverage = questions == 0 ? 0.0
                : Math.round((1.0 - (double) guides / questions) * 1000) / 10.0;
        return Map.of(
                "questionCount", questions,
                "cardCount", cards,
                "guideCount", guides,
                "catalogCoverageRate", coverage);   // % — "카탈로그가 답한 비율"
    }

    private long count(String sql) {
        Long v = jdbc.queryForObject(sql, Long.class);
        return v == null ? 0L : v;
    }
}
