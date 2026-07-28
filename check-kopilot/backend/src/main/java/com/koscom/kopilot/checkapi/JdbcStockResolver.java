package com.koscom.kopilot.checkapi;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class JdbcStockResolver implements StockResolver {

    private final JdbcTemplate jdbc;

    public JdbcStockResolver(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /**
     * 되묻기 칩을 누르면 프론트가 "삼성전자(005930) 기준으로 진행해줘"를 보내고, LLM이 거기서
     * 대상을 다시 뽑아 tool에 넣는다. 이름만 뽑아 주면 좋지만 통째로 넘어오면
     * {@code LIKE '%삼성전자(005930)%'}가 0건이라 방금 고른 종목이 "없는 종목"이 된다.
     * 괄호 안을 먼저 코드로 시도해, 이름만·코드만·{@code 이름(코드)} 중 무엇이 와도 맞춘다.
     */
    private static final java.util.regex.Pattern NAME_WITH_CODE =
            java.util.regex.Pattern.compile("^(.+?)\\s*\\(\\s*([A-Za-z0-9]{4,12})\\s*\\)$");

    @Override
    public StockInfo resolve(String nameOrCode) {
        String q = nameOrCode.trim();
        java.util.regex.Matcher m = NAME_WITH_CODE.matcher(q);
        if (m.matches()) {
            List<StockInfo> byParenCode = query("SELECT * FROM stock_master WHERE code = ?", m.group(2));
            if (byParenCode.size() == 1) return byParenCode.get(0);
            q = m.group(1).trim();   // 코드가 안 맞으면 이름 부분으로 평소 경로를 탄다
        }
        // 1) 코드 정확일치  2) 이름 정확일치  3) 별칭 정확일치  4) 부분일치
        // 별칭이 이름보다 뒤인 이유: 공식 상장명이 항상 이긴다("KT"는 별칭 "케이티"보다 우선).
        List<StockInfo> byCode = query("SELECT * FROM stock_master WHERE code = ?", q);
        if (byCode.size() == 1) return byCode.get(0);
        List<StockInfo> exact = query("SELECT * FROM stock_master WHERE name = ?", q);
        if (exact.size() == 1) return exact.get(0);
        List<StockInfo> byAlias = query("""
                SELECT m.* FROM stock_master m
                JOIN stock_alias a ON a.code = m.code
                WHERE a.alias = ?""", q);
        if (byAlias.size() == 1) return byAlias.get(0);
        List<StockInfo> partial = search(q);
        if (partial.isEmpty()) throw new StockNotFoundException(q, suggestionsFor(q));
        if (partial.size() == 1) return partial.get(0);
        throw new AmbiguousStockException(q, partial);
    }

    /**
     * 못 찾았을 때 대신 보여줄 후보. 앞 2글자만 잘라 재검색하면 "이차전지"가 "이차"(0건)로 떨어져
     * 제안이 빈 채로 나갔다. 긴 것부터 모든 연속 부분문자열을 훑어 첫 히트를 쓴다
     * ("이차전지" → "이차전"·"차전지" 순으로 내려가 "차전지"에서 걸린다).
     */
    private List<StockInfo> suggestionsFor(String q) {
        for (int len = q.length() - 1; len >= 2; len--) {
            for (int start = 0; start + len <= q.length(); start++) {
                List<StockInfo> hit = search(q.substring(start, start + len));
                if (!hit.isEmpty()) return hit;
            }
        }
        return List.of();
    }

    /**
     * 되묻기 칩에 올릴 후보. 순서가 곧 사용자가 보는 버튼 순서다.
     *
     * <p>정렬 기준이 이름 길이뿐이던 시절, 마스터가 13종목 데모 풀에서 전 상장 4,392행으로 늘자
     * "삼성"의 첫 후보가 삼성공조가 되고 삼성전자는 다섯 번째로 밀렸다. 97건 매칭 중 STOCK은
     * 26건뿐이고 나머지는 ETN·ETF였다. 관련도 순으로 세운다.
     *
     * <p>유형·우선주·스팩은 제외가 아니라 후순위다 — "KODEX 200" 같은 정당한 ETF 질의를 막으면 안 된다.
     *
     * <p>우선주 판정에 이름이 아니라 종목코드 끝자리를 쓰는 이유: 정규식 {@code 우B?$}는
     * 성우(458650)·에코글로우·이오플로우 같은 보통주를 오탐한다. 코드 규칙(보통주 0, 우선주 5/7/9/K/L)은
     * 실 DB의 STOCK 2,766건 전수에서 오탐·미탐 0건이었다.
     *
     * <p>UNION 바깥에서 stock_priority를 한 번만 조인한다 — 안쪽에서 양쪽에 조인하면
     * MySQL이 같은 테이블 재참조를 거부한다.
     */
    @Override
    public List<StockInfo> search(String name) {
        // MySQL 기본 콜레이션(utf8mb4_0900_ai_ci)은 대소문자를 구분하지 않으므로 LIKE로 충분하다.
        // 별칭도 후보에 넣는다 — "네이버"로 검색해도 되묻기 칩에 NAVER가 떠야 한다.
        String q = name.trim();
        String like = "%" + q + "%";
        String prefix = q + "%";
        return query("""
                SELECT c.* FROM (
                    SELECT m.* FROM stock_master m WHERE m.name LIKE ?
                    UNION
                    SELECT m.* FROM stock_master m
                        JOIN stock_alias a ON a.code = m.code WHERE a.alias LIKE ?
                ) c
                LEFT JOIN stock_priority p ON p.code = c.code
                ORDER BY
                    (c.name = ?) DESC,
                    COALESCE(p.priority, 0) DESC,
                    (c.name LIKE ?) DESC,
                    CASE c.type WHEN 'ETF' THEN 1 WHEN 'ETN' THEN 2 ELSE 0 END,
                    (c.type = 'STOCK' AND RIGHT(c.code, 1) <> '0') ASC,
                    (c.name LIKE '%스팩%') ASC,
                    CHAR_LENGTH(c.name), c.name
                LIMIT 5""", like, like, q, prefix);
    }

    private List<StockInfo> query(String sql, Object... args) {
        return jdbc.query(sql, (rs, i) -> new StockInfo(
                rs.getString("code"), rs.getString("name"),
                rs.getString("market"), rs.getString("type")), args);
    }
}
