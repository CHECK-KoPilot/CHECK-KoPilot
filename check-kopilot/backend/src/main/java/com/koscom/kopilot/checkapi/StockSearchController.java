package com.koscom.kopilot.checkapi;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 단축키 폼의 종목 자동완성. 되묻기와 같은 검색기를 쓴다 — 결과가 갈리면 안 된다. */
@RestController
public class StockSearchController {

    private static final int MIN_QUERY_LENGTH = 2;
    private static final int MAX_LIMIT = 20;

    private final StockResolver stocks;

    public StockSearchController(StockResolver stocks) { this.stocks = stocks; }

    @GetMapping("/api/stocks")
    public List<StockInfo> search(@RequestParam("q") String q,
                                  @RequestParam(value = "limit", defaultValue = "8") int limit) {
        String query = q == null ? "" : q.trim();
        if (query.length() < MIN_QUERY_LENGTH) return List.of();
        return stocks.search(query).stream()
                .limit(Math.clamp(limit, 1, MAX_LIMIT))
                .toList();
    }
}
