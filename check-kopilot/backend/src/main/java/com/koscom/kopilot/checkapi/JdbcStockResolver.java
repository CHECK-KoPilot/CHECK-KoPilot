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
        // 1) 코드 정확일치  2) 이름 정확일치  3) 부분일치
        List<StockInfo> byCode = query("SELECT * FROM stock_master WHERE code = ?", q);
        if (byCode.size() == 1) return byCode.get(0);
        List<StockInfo> exact = query("SELECT * FROM stock_master WHERE name = ?", q);
        if (exact.size() == 1) return exact.get(0);
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
        return query("SELECT * FROM stock_master WHERE name LIKE ? ORDER BY CHAR_LENGTH(name), name LIMIT 5",
                "%" + name.trim() + "%");
    }

    private List<StockInfo> query(String sql, Object... args) {
        return jdbc.query(sql, (rs, i) -> new StockInfo(
                rs.getString("code"), rs.getString("name"),
                rs.getString("market"), rs.getString("type")), args);
    }
}
