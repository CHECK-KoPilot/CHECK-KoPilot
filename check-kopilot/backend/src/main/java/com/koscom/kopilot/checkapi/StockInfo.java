package com.koscom.kopilot.checkapi;

public record StockInfo(String code, String name, String market, String type) {
    public boolean isEtf()   { return "ETF".equals(type); }
    public boolean isIndex() { return "INDEX".equals(type); }
}
