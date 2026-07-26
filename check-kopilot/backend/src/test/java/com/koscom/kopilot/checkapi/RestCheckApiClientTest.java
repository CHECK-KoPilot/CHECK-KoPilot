package com.koscom.kopilot.checkapi;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * {@link RestCheckApiClient}의 파싱·정렬 회귀 테스트 (이슈 #21).
 *
 * <p>실제 HTTP 호출 없이, 요청 인터셉터로 CHECK API 응답 봉투를 흉내 낸 고정 JSON을 주입한다.
 * 네트워크 접근이 전혀 없어 CI/로컬 어디서든 안정적으로 돈다.
 */
class RestCheckApiClientTest {

    private final StockInfo samsung = new StockInfo("005930", "삼성전자", "KOSPI", "STOCK");
    private final LocalDate from = LocalDate.parse("2026-07-20");
    private final LocalDate to = LocalDate.parse("2026-07-22");

    private static final String EMPTY_OK = """
            {"success":true,"results":[]}
            """;

    private CheckApiProperties props() {
        return new CheckApiProperties("https://checkapi.koscom.co.kr", "cust", "key",
                Map.of("stock-daily", "/stock/m001/hist_info",
                        "kosdaq-daily", "/stock/m003/hist_info",
                        "index-daily", "/stock/m002/hist_info",
                        "kosdaq-index-daily", "/stock/m004/hist_info"));
    }

    private RestCheckApiClient clientReturning(String json) {
        return clientReturning(json, new ArrayList<>());
    }

    /** 응답은 고정 JSON으로 주고, 나간 요청(URI·body)은 captured에 담아 검증한다. */
    private RestCheckApiClient clientReturning(String json, List<String> captured) {
        ClientHttpRequestInterceptor stub = (request, body, execution) -> {
            captured.add(request.getURI().getPath());
            captured.add(new String(body, StandardCharsets.UTF_8));
            MockClientHttpResponse response = new MockClientHttpResponse(
                    json.getBytes(StandardCharsets.UTF_8), HttpStatus.OK);
            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            return response;
        };
        return new RestCheckApiClient(props(), RestClient.builder().requestInterceptor(stub));
    }

    @Test
    void dailyQuotes_reordersDescendingResultsToAscendingByDate() {
        // CHECK API는 최신→과거 내림차순으로 준다. 부호가 뒤집히는 버그를 잡기 위한 핵심 케이스.
        String json = """
                {"success":true,"results":[
                  {"F12506":20260722,"F15001":"260500","F15009":"260000","F15010":"261000","F15011":"259500","F15015":"1000000"},
                  {"F12506":20260721,"F15001":"257000","F15009":"255000","F15010":"258000","F15011":"254500","F15015":"900000"},
                  {"F12506":20260720,"F15001":"250000","F15009":"249000","F15010":"252000","F15011":"248000","F15015":"800000"}
                ]}
                """;

        List<DailyQuote> quotes = clientReturning(json).dailyQuotes(samsung, from, to);

        assertThat(quotes).hasSize(3);
        assertThat(quotes).extracting(DailyQuote::date).containsExactly(
                LocalDate.parse("2026-07-20"),
                LocalDate.parse("2026-07-21"),
                LocalDate.parse("2026-07-22"));
        assertThat(quotes.get(0).close()).isEqualTo(250000.0);
        assertThat(quotes.get(2).close()).isEqualTo(260500.0);
    }

    @Test
    void dailyQuotes_parsesStringNumericFields() {
        String json = """
                {"success":true,"results":[
                  {"F12506":20260722,"F15001":"285000","F15009":"280000","F15010":"286000","F15011":"279500","F15015":"123456"}
                ]}
                """;

        DailyQuote quote = clientReturning(json).dailyQuotes(samsung, from, to).get(0);

        assertThat(quote.close()).isEqualTo(285000.0);
        assertThat(quote.open()).isEqualTo(280000.0);
        assertThat(quote.high()).isEqualTo(286000.0);
        assertThat(quote.low()).isEqualTo(279500.0);
        assertThat(quote.volume()).isEqualTo(123456L);
    }

    @Test
    void successFalse_throwsCheckApiException() {
        String json = """
                {"success":false,"message":"cust_id 또는 auth_key가 정확하지 않습니다."}
                """;

        assertThatThrownBy(() -> clientReturning(json).dailyQuotes(samsung, from, to))
                .isInstanceOf(CheckApiException.class);
    }

    @Test
    void successTrueWithEmptyResults_returnsEmptyList() {
        assertThat(clientReturning(EMPTY_OK).dailyQuotes(samsung, from, to)).isEmpty();
    }

    @Test
    void stock_sendsShortCodeAsJcode() {
        List<String> captured = new ArrayList<>();

        clientReturning(EMPTY_OK, captured).dailyQuotes(samsung, from, to);

        assertThat(captured.get(0)).isEqualTo("/stock/m001/hist_info");
        assertThat(captured.get(1)).contains("\"jcode\":\"005930\"");
    }

    // 지수의 jcode는 마스터 식별자(KOSPI)가 아니라 업종코드 1이다. 이 변환이 빠지면
    // "삼성전자 vs 코스피" 같은 질문에서 지수 쪽만 조용히 빈 결과가 돌아온다.
    @Test
    void kospiIndex_sendsSectorCodeOneToM002() {
        StockInfo kospi = new StockInfo("KOSPI", "코스피", "KOSPI", "INDEX");
        List<String> captured = new ArrayList<>();

        clientReturning(EMPTY_OK, captured).dailyQuotes(kospi, from, to);

        assertThat(captured.get(0)).isEqualTo("/stock/m002/hist_info");
        assertThat(captured.get(1)).contains("\"jcode\":\"1\"");
    }

    @Test
    void kosdaqIndex_sendsSectorCodeOneToM004() {
        StockInfo kosdaq = new StockInfo("KOSDAQ", "코스닥", "KOSDAQ", "INDEX");
        List<String> captured = new ArrayList<>();

        clientReturning(EMPTY_OK, captured).dailyQuotes(kosdaq, from, to);

        assertThat(captured.get(0)).isEqualTo("/stock/m004/hist_info");
        assertThat(captured.get(1)).contains("\"jcode\":\"1\"");
    }

    /**
     * 이슈 #58. 2026-07-24 KODEX 200 응답에 NAV가 빈 중복 행이 실제로 섞여 왔다.
     * 그 0이 괴리율 나눗셈에 닿으면 Infinity가 되고, round4가 이를 Long.MAX_VALUE/10⁴ 이라는
     * "그럴듯한 숫자"로 바꿔 카드에 실어 보냈다. 흠은 이 클래스 안에서 끝나야 한다.
     */
    @Test
    void etfNav_dropsRowWithMissingNavAndKeepsOneRowPerDate() {
        StockInfo kodex = new StockInfo("069500", "KODEX 200", "KOSPI", "ETF");
        String json = """
                {"success":true,"results":[
                  {"F12506":20260724,"F15001":"106365"},
                  {"F12506":20260724,"F15001":"106365","F15301":"106253.71"},
                  {"F12506":20260723,"F15001":"113230","F15301":"113375.02"}
                ]}
                """;

        List<NavQuote> navs = clientReturning(json).etfNav(kodex, from, to);

        assertThat(navs).hasSize(2);
        assertThat(navs).extracting(NavQuote::date)
                .containsExactly(LocalDate.parse("2026-07-23"), LocalDate.parse("2026-07-24"));
        assertThat(navs).allSatisfy(n -> assertThat(n.nav()).isGreaterThan(0));
    }

    @Test
    void dailyQuotes_dropsRowWithBlankClose() {
        // 빈 문자열도 결측으로 읽는다 — new BigDecimal("")은 0이 아니라 예외라
        // 응답 한 칸이 비었다고 호출 전체가 죽으면 안 된다. 종가가 0이면 기간수익률이 (last/0 - 1)로 터진다
        String json = """
                {"success":true,"results":[
                  {"F12506":20260722,"F15001":"260500","F15009":"260000","F15010":"261000","F15011":"259500","F15015":"1000000"},
                  {"F12506":20260721,"F15001":"","F15009":"255000","F15010":"258000","F15011":"254500","F15015":"900000"},
                  {"F12506":20260720,"F15001":"250000","F15009":"249000","F15010":"252000","F15011":"248000","F15015":"800000"}
                ]}
                """;

        List<DailyQuote> quotes = clientReturning(json).dailyQuotes(samsung, from, to);

        assertThat(quotes).extracting(DailyQuote::date)
                .containsExactly(LocalDate.parse("2026-07-20"), LocalDate.parse("2026-07-22"));
    }

    @Test
    void unmappedIndex_throwsCheckApiException() {
        StockInfo unknown = new StockInfo("KRX300", "KRX 300", "KOSPI", "INDEX");

        assertThatThrownBy(() -> clientReturning(EMPTY_OK).dailyQuotes(unknown, from, to))
                .isInstanceOf(CheckApiException.class)
                .hasMessageContaining("KRX300");
    }
}
