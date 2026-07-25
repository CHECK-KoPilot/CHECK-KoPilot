package com.koscom.kopilot.checkapi;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 실제 CHECK API 호출 구현. 외부 명세 의존부는 이 클래스에만 존재한다.
 *
 * <p>호출 규약(2026-07-22 실호출로 확정, docs/check-api/README.md 참조):
 * POST 전용(GET은 인증 실패로 거부), 인증정보(cust_id/auth_key)는 헤더가 아니라 JSON payload에 담는다.
 * 응답 봉투는 {@code {"success":true,"results":[...]}}이며 HTTP status는 실패해도 항상 200이므로
 * 반드시 success 필드로 판정한다. results의 각 항목은 F코드를 key로 갖고 값은 대부분 문자열이다
 * (F12506 입회일만 정수). 존재하지 않는 종목코드는 에러가 아니라 results:[]로 온다.
 * 시계열은 최신→과거 내림차순으로 오므로 클라이언트에서 날짜 오름차순으로 재정렬한다.
 *
 * <p>지수 백오프 재시도 3회 (네트워크 오류에 한함 — success:false는 재시도하지 않는다).
 */
public class RestCheckApiClient implements CheckApiClient {

    private static final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String DAILY_DATA_LIST = "F12506,F15001,F15009,F15010,F15011,F15015";
    private static final String NAV_DATA_LIST = "F12506,F15001,F15301";

    private final RestClient rest;
    private final CheckApiProperties props;

    public RestCheckApiClient(CheckApiProperties props) {
        this(props, RestClient.builder());
    }

    /** 테스트에서 요청 인터셉터/{@code MockRestServiceServer}를 주입하기 위한 생성자. */
    RestCheckApiClient(CheckApiProperties props, RestClient.Builder builder) {
        this.props = props;
        this.rest = builder.baseUrl(props.baseUrl()).build();
    }

    @Override
    public List<DailyQuote> dailyQuotes(StockInfo instrument, LocalDate from, LocalDate to) {
        JsonNode results = results(dailyPath(instrument), requestBody(instrument, from, to, DAILY_DATA_LIST));
        List<DailyQuote> out = new ArrayList<>();
        for (JsonNode r : results) {
            out.add(new DailyQuote(parseYmd(r.get("F12506")),
                    parseDecimal(r.get("F15009")), parseDecimal(r.get("F15010")),
                    parseDecimal(r.get("F15011")), parseDecimal(r.get("F15001")),
                    parseLong(r.get("F15015"))));
        }
        out.sort(Comparator.comparing(DailyQuote::date));
        return out;
    }

    @Override
    public List<NavQuote> etfNav(StockInfo etf, LocalDate from, LocalDate to) {
        JsonNode results = results(dailyPath(etf), requestBody(etf, from, to, NAV_DATA_LIST));
        List<NavQuote> out = new ArrayList<>();
        for (JsonNode r : results) {
            out.add(new NavQuote(parseYmd(r.get("F12506")),
                    parseDecimal(r.get("F15001")), parseDecimal(r.get("F15301"))));
        }
        out.sort(Comparator.comparing(NavQuote::date));
        return out;
    }

    /** m001(거래소 종목)/m002(거래소 업종)/m003(코스닥 종목)/m004(코스닥 업종) 중 인스트루먼트에 맞는 hist_info 경로. */
    private String dailyPath(StockInfo instrument) {
        boolean kosdaq = "KOSDAQ".equalsIgnoreCase(instrument.market());
        String key = instrument.isIndex()
                ? (kosdaq ? "kosdaq-index-daily" : "index-daily")
                : (kosdaq ? "kosdaq-daily" : "stock-daily");
        String path = props.paths().get(key);
        if (path == null) {
            throw new CheckApiException("checkapi.paths." + key + " 설정이 없습니다.");
        }
        return path;
    }

    private Map<String, Object> requestBody(StockInfo instrument, LocalDate from, LocalDate to, String dataList) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("cust_id", props.custId());
        body.put("auth_key", props.apiKey());
        body.put("jcode", instrument.code());
        body.put("sdate", from.format(YMD));
        body.put("edate", to.format(YMD));
        body.put("data_list", dataList);
        return body;
    }

    private JsonNode results(String path, Map<String, Object> body) {
        JsonNode root = callWithRetry(path, body);
        if (!root.path("success").asBoolean(false)) {
            throw new CheckApiException("CHECK API 오류: " + root.path("message").asText("알 수 없는 오류"));
        }
        return root.path("results");
    }

    private JsonNode callWithRetry(String path, Map<String, Object> body) {
        RuntimeException last = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                return rest.post()
                        .uri(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .body(JsonNode.class);
            } catch (RuntimeException e) {
                last = e;
                try { Thread.sleep(300L * (1L << attempt)); }
                catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
            }
        }
        throw new CheckApiException("CHECK API 호출 실패: " + path, last);
    }

    private LocalDate parseYmd(JsonNode node) {
        return LocalDate.parse(node.asText(), YMD);
    }

    private double parseDecimal(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return 0.0;
        return new BigDecimal(node.asText()).doubleValue();
    }

    private long parseLong(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return 0L;
        return new BigDecimal(node.asText()).longValue();
    }
}
