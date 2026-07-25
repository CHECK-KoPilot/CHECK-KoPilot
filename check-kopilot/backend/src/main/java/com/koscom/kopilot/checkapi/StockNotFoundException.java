package com.koscom.kopilot.checkapi;

import java.util.List;

public class StockNotFoundException extends RuntimeException {
    private final String query;
    private final List<StockInfo> suggestions;

    public StockNotFoundException(String query, List<StockInfo> suggestions) {
        super("종목명 미매칭: " + query);
        this.query = query;
        this.suggestions = suggestions;
    }
    public String query() { return query; }
    public List<StockInfo> suggestions() { return suggestions; }
}
