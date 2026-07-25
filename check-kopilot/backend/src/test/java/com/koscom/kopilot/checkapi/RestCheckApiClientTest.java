package com.koscom.kopilot.checkapi;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
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

    private CheckApiProperties props() {
        return new CheckApiProperties("https://checkapi.koscom.co.kr", "cust", "key",
                Map.of("stock-daily", "/stock/m001/hist_info"));
    }

    private RestCheckApiClient clientReturning(String json) {
        ClientHttpRequestInterceptor stub = (request, body, execution) -> {
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
        String json = """
                {"success":true,"results":[]}
                """;

        assertThat(clientReturning(json).dailyQuotes(samsung, from, to)).isEmpty();
    }
}
