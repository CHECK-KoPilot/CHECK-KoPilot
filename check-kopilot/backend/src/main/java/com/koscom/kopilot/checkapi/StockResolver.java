package com.koscom.kopilot.checkapi;

import java.util.List;

public interface StockResolver {
    /** 이름/코드로 단일 종목 확정. 다건이면 AmbiguousStockException, 0건이면 StockNotFoundException. */
    StockInfo resolve(String nameOrCode);
    List<StockInfo> search(String name);
}
