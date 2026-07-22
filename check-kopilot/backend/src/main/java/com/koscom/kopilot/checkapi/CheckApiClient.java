package com.koscom.kopilot.checkapi;

import java.time.LocalDate;
import java.util.List;

public interface CheckApiClient {
    List<DailyQuote> dailyQuotes(StockInfo instrument, LocalDate from, LocalDate to);
    List<NavQuote> etfNav(StockInfo etf, LocalDate from, LocalDate to);
}
