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
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 실제 CHECK API 호출 구현. 외부 명세 의존부는 이 클래스에만 존재한다.
 *
 * <p>호출 규약(2026-07-22 실호출로 확정, docs/check-api/README.md 참조):
 * POST 전용(GET은 인증 실패로 거부), 인증정보(cust_id/auth_key)는 헤더가 아니라 JSON payload에 담는다.
 * 응답 봉투는 {@code {"success":true,"results":[...]}}이며 HTTP status는 실패해도 항상 200이므로
 * 반드시 success 필드로 판정한다. results의 각 항목은 F코드를 key로 갖고 값은 대부분 문자열이다
 * (F12506 입회일만 정수). 존재하지 않는 종목코드는 에러가 아니라 results:[]로 온다.
 * 시계열은 최신→과거 내림차순으로 오므로 클라이언트에서 날짜 오름차순으로 재정렬한다.
 * jcode는 종목이면 단축코드, 지수면 업종코드다 — 종목 마스터의 지수 식별자(KOSPI/KOSDAQ)를 여기서 변환한다.
 *
 * <p>지수 백오프 재시도 3회 (네트워크 오류에 한함 — success:false는 재시도하지 않는다).
 */
public class RestCheckApiClient implements CheckApiClient {

    private static final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String DAILY_DATA_LIST = "F12506,F15001,F15009,F15010,F15011,F15015";
    private static final String NAV_DATA_LIST = "F12506,F15001,F15301";

    /**
     * 대표지수의 업종코드. 종목 마스터는 지수를 KOSPI/KOSDAQ 문자열로 식별하지만 CHECK API가 받는 jcode는
     * 업종코드이며, 대표지수는 코스피·코스닥 모두 1이다(시장 구분은 m002/m004 경로가 담당).
     */
    private static final Map<String, String> INDEX_JCODES = Map.of("KOSPI", "1", "KOSDAQ", "1");

    /** 업종지수 식별자 {@code <시장>-<업종코드>} (예: {@code KOSPI-8} 화학, {@code KOSDAQ-65} 화학) */
    private static final Pattern SECTOR_INDEX = Pattern.compile("^(?:KOSPI|KOSDAQ)-(\\d+)$");

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
        return usableRows(out, DailyQuote::date,
                q -> q.close() > 0 && q.high() > 0 && q.low() > 0);
    }

    @Override
    public List<NavQuote> etfNav(StockInfo etf, LocalDate from, LocalDate to) {
        JsonNode results = results(dailyPath(etf), requestBody(etf, from, to, NAV_DATA_LIST));
        List<NavQuote> out = new ArrayList<>();
        for (JsonNode r : results) {
            out.add(new NavQuote(parseYmd(r.get("F12506")),
                    parseDecimal(r.get("F15001")), parseDecimal(r.get("F15301"))));
        }
        return usableRows(out, NavQuote::date, n -> n.marketPrice() > 0 && n.nav() > 0);
    }

    /**
     * 응답 행을 계산에 쓸 수 있는 것만 남기고 날짜 오름차순으로 돌려준다.
     *
     * <p>CHECK API는 같은 날짜를 두 번 내려주거나 필드를 비워 보낼 때가 있다. 실제로 2026-07-24
     * KODEX 200 응답에 NAV가 빈 중복 행이 섞여 왔다(이슈 #58). 결측을 {@code parseDecimal}이 0으로
     * 바꾸고, 그 0이 계산에 닿으면 나눗셈이 Infinity가 되어 카드에 그럴듯한 가짜 숫자가 찍힌다.
     * 벤더 응답의 흠은 이 클래스 밖으로 내보내지 않는다 — 지표 실행기는 성한 행만 본다.
     *
     * <p>중복 날짜는 먼저 온 성한 행을 남긴다. 응답이 최신→과거 순이라 같은 날짜끼리는
     * 벤더가 앞세운 행이 대표값이라고 본다.
     */
    private <T> List<T> usableRows(List<T> rows, Function<T, LocalDate> date, Predicate<T> usable) {
        Map<LocalDate, T> byDate = new LinkedHashMap<>();
        for (T row : rows) {
            if (usable.test(row)) byDate.putIfAbsent(date.apply(row), row);
        }
        return byDate.values().stream().sorted(Comparator.comparing(date)).toList();
    }

    /**
     * m001(거래소 종목)/m002(거래소 업종)/m003(코스닥 종목)/m004(코스닥 업종) 중 인스트루먼트에 맞는 hist_info 경로.
     * 지수도 소속 시장(market)으로 갈린다 — 코스닥 지수는 m002가 아니라 m004다.
     */
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

    /** 종목·ETF·ETN은 단축코드가 곧 jcode지만, 지수는 마스터 식별자를 업종코드로 바꿔 보낸다. */
    private String jcode(StockInfo instrument) {
        if (!instrument.isIndex()) {
            return instrument.code();
        }
        String code = instrument.code().toUpperCase();
        Matcher sector = SECTOR_INDEX.matcher(code);
        if (sector.matches()) {
            return sector.group(1);
        }
        String indexCode = INDEX_JCODES.get(code);
        if (indexCode == null) {
            throw new CheckApiException("지수 업종코드 매핑이 없습니다: " + instrument.code());
        }
        return indexCode;
    }

    private Map<String, Object> requestBody(StockInfo instrument, LocalDate from, LocalDate to, String dataList) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("cust_id", props.custId());
        body.put("auth_key", props.apiKey());
        body.put("jcode", jcode(instrument));
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

    /**
     * 결측은 0으로 읽는다. 빈 문자열도 결측으로 본다 — {@code new BigDecimal("")}은
     * 0이 아니라 NumberFormatException이라 응답 한 칸이 비었다고 호출 전체가 죽는다.
     * 0으로 읽은 값은 {@link #usableRows}가 걸러 계산에 닿지 않는다.
     */
    private double parseDecimal(JsonNode node) {
        String text = text(node);
        return text == null ? 0.0 : new BigDecimal(text).doubleValue();
    }

    private long parseLong(JsonNode node) {
        String text = text(node);
        return text == null ? 0L : new BigDecimal(text).longValue();
    }

    /** 값이 없거나 공백뿐이면 null */
    private String text(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        String text = node.asText().trim();
        return text.isEmpty() ? null : text;
    }
}
