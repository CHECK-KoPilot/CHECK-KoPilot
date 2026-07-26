package com.koscom.kopilot.checkapi;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class JdbcStockResolver implements StockResolver {

    private final JdbcTemplate jdbc;

    public JdbcStockResolver(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public StockInfo resolve(String nameOrCode) {
        String q = nameOrCode.trim();
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
        if (partial.isEmpty()) {
            List<StockInfo> sugg = q.length() >= 2 ? search(q.substring(0, 2)) : List.of();
            throw new StockNotFoundException(q, sugg);
        }
        if (partial.size() == 1) return partial.get(0);
        throw new AmbiguousStockException(q, partial);
    }

    @Override
    public List<StockInfo> search(String name) {
        // MySQL 기본 콜레이션(utf8mb4_0900_ai_ci)은 대소문자를 구분하지 않으므로 LIKE로 충분하다.
        // 별칭도 후보에 넣는다 — "네이버"로 검색해도 되묻기 칩에 NAVER가 떠야 한다.
        String like = "%" + name.trim() + "%";
        return query("""
                SELECT * FROM (
                    SELECT m.* FROM stock_master m WHERE m.name LIKE ?
                    UNION
                    SELECT m.* FROM stock_master m
                        JOIN stock_alias a ON a.code = m.code WHERE a.alias LIKE ?
                ) c ORDER BY CHAR_LENGTH(c.name), c.name LIMIT 5""", like, like);
    }

    private List<StockInfo> query(String sql, Object... args) {
        return jdbc.query(sql, (rs, i) -> new StockInfo(
                rs.getString("code"), rs.getString("name"),
                rs.getString("market"), rs.getString("type")), args);
    }
}
