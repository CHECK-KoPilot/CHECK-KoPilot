package com.koscom.kopilot.checkapi;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 2계층 캐시 래퍼.
 *  - shortTerm(Redis, TTL): 반복 질문의 지연·쿼터 절감. hit이면 외부 호출 자체를 생략한다.
 *  - fallback(MySQL, 무기한): CHECK API 장애 시 마지막 성공 응답으로 데모를 살린다(스펙 10절).
 */
public class CachingCheckApiClient implements CheckApiClient {

    /** Redis/JDBC 구현을 갈아끼우기 위한 최소 인터페이스 (단위 테스트는 Map 구현 주입) */
    public interface KeyValueStore {
        void put(String key, String json);
        Optional<String> get(String key);
    }

    private final CheckApiClient delegate;
    private final KeyValueStore shortTerm;
    private final KeyValueStore fallback;
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    public CachingCheckApiClient(CheckApiClient delegate, KeyValueStore shortTerm, KeyValueStore fallback) {
        this.delegate = delegate;
        this.shortTerm = shortTerm;
        this.fallback = fallback;
    }

    @Override
    public List<DailyQuote> dailyQuotes(StockInfo instrument, LocalDate from, LocalDate to) {
        String key = "daily:%s:%s:%s".formatted(instrument.code(), from, to);
        return fetch(key, () -> delegate.dailyQuotes(instrument, from, to),
                new TypeReference<List<DailyQuote>>() {});
    }

    @Override
    public List<NavQuote> etfNav(StockInfo etf, LocalDate from, LocalDate to) {
        String key = "nav:%s:%s:%s".formatted(etf.code(), from, to);
        return fetch(key, () -> delegate.etfNav(etf, from, to),
                new TypeReference<List<NavQuote>>() {});
    }

    private <T> T fetch(String key, java.util.function.Supplier<T> call, TypeReference<T> type) {
        Optional<String> hot = shortTerm.get(key);
        if (hot.isPresent()) return read(hot.get(), type);
        try {
            T fresh = call.get();
            String json = write(fresh);
            shortTerm.put(key, json);
            fallback.put(key, json);
            return fresh;
        } catch (RuntimeException e) {
            Optional<String> snapshot = fallback.get(key);
            if (snapshot.isPresent()) return read(snapshot.get(), type);
            throw e;
        }
    }

    private String write(Object o) {
        try { return mapper.writeValueAsString(o); }
        catch (Exception e) { throw new CheckApiException("캐시 직렬화 실패", e); }
    }

    private <T> T read(String json, TypeReference<T> type) {
        try { return mapper.readValue(json, type); }
        catch (Exception e) { throw new CheckApiException("캐시 역직렬화 실패", e); }
    }
}
