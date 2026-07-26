package com.koscom.kopilot.catalog;

import com.koscom.kopilot.checkapi.AmbiguousStockException;
import com.koscom.kopilot.checkapi.StockInfo;
import com.koscom.kopilot.checkapi.StockNotFoundException;
import com.koscom.kopilot.checkapi.StockResolver;

import java.util.List;

public final class TestStocks {

    public static final List<StockInfo> ALL = List.of(
            new StockInfo("005930", "삼성전자", "KOSPI", "STOCK"),
            new StockInfo("005380", "현대차", "KOSPI", "STOCK"),
            new StockInfo("035720", "카카오", "KOSPI", "STOCK"),
            new StockInfo("086520", "에코프로", "KOSDAQ", "STOCK"),
            new StockInfo("247540", "에코프로비엠", "KOSDAQ", "STOCK"),
            new StockInfo("066970", "엘앤에프", "KOSDAQ", "STOCK"),
            new StockInfo("003670", "포스코퓨처엠", "KOSPI", "STOCK"),
            new StockInfo("360750", "TIGER 미국S&P500", "KOSPI", "ETF"),
            new StockInfo("KOSPI", "코스피", "KOSPI", "INDEX"));

    private TestStocks() {
    }

    public static StockResolver resolver() {
        return new StockResolver() {
            @Override
            public StockInfo resolve(String nameOrCode) {
                List<StockInfo> exact = ALL.stream()
                        .filter(stock -> stock.name().equals(nameOrCode) || stock.code().equals(nameOrCode))
                        .toList();
                if (exact.size() == 1) {
                    return exact.get(0);
                }

                List<StockInfo> partial = search(nameOrCode);
                if (partial.size() == 1) {
                    return partial.get(0);
                }
                if (partial.isEmpty()) {
                    throw new StockNotFoundException(nameOrCode, List.of());
                }
                throw new AmbiguousStockException(nameOrCode, partial);
            }

            @Override
            public List<StockInfo> search(String name) {
                return ALL.stream().filter(stock -> stock.name().contains(name)).toList();
            }
        };
    }
}
