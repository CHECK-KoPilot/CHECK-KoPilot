package com.koscom.kopilot.checkapi;

import java.util.List;

public class AmbiguousStockException extends RuntimeException {
    private final String query;
    private final List<StockInfo> candidates;

    public AmbiguousStockException(String query, List<StockInfo> candidates) {
        super("종목명 다건 매칭: " + query);
        this.query = query;
        this.candidates = candidates;
    }
    public String query() { return query; }
    public List<StockInfo> candidates() { return candidates; }
}
