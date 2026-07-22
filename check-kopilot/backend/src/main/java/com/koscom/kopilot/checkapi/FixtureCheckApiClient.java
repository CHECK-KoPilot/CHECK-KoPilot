package com.koscom.kopilot.checkapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** classpath:fixtures/daily-{code}.json / nav-{code}.json 을 읽는 구현. 테스트·데모 폴백용. */
public class FixtureCheckApiClient implements CheckApiClient {

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public List<DailyQuote> dailyQuotes(StockInfo instrument, LocalDate from, LocalDate to) {
        JsonNode root = read("fixtures/daily-" + instrument.code() + ".json");
        List<DailyQuote> result = new ArrayList<>();
        for (JsonNode q : root.get("quotes")) {
            LocalDate d = LocalDate.parse(q.get("date").asText());
            if (!d.isBefore(from) && !d.isAfter(to)) {
                result.add(new DailyQuote(d, q.get("open").asDouble(), q.get("high").asDouble(),
                        q.get("low").asDouble(), q.get("close").asDouble(), q.get("volume").asLong()));
            }
        }
        return result;
    }

    @Override
    public List<NavQuote> etfNav(StockInfo etf, LocalDate from, LocalDate to) {
        JsonNode root = read("fixtures/nav-" + etf.code() + ".json");
        List<NavQuote> result = new ArrayList<>();
        for (JsonNode n : root.get("navs")) {
            LocalDate d = LocalDate.parse(n.get("date").asText());
            if (!d.isBefore(from) && !d.isAfter(to)) {
                result.add(new NavQuote(d, n.get("marketPrice").asDouble(), n.get("nav").asDouble()));
            }
        }
        return result;
    }

    private JsonNode read(String path) {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(path)) {
            if (in == null) throw new CheckApiException("픽스처 없음: " + path);
            return mapper.readTree(in);
        } catch (java.io.IOException e) {
            throw new CheckApiException("픽스처 읽기 실패: " + path, e);
        }
    }
}
