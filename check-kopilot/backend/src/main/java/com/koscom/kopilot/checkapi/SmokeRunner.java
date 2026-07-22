package com.koscom.kopilot.checkapi;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.time.LocalDate;
import java.util.List;

/**
 * 개발 중 실 CHECK API 대조용. 기본 비활성 — SPRING_PROFILES_ACTIVE=smoke 로만 실행된다.
 * Task 2 Step 7 검증 항목 4가지를 순서대로 호출해 콘솔에 출력한다.
 */
@Configuration
@Profile("smoke")
public class SmokeRunner {

    @Bean
    CommandLineRunner smoke(CheckApiClient client) {
        return args -> {
            LocalDate from = LocalDate.now().minusDays(10);
            LocalDate to = LocalDate.now();

            System.out.println("=== 1) 삼성전자(005930) 일별 시세 (/stock/m001/hist_info) ===");
            var samsung = new StockInfo("005930", "삼성전자", "KOSPI", "STOCK");
            List<DailyQuote> samsungQuotes = client.dailyQuotes(samsung, from, to);
            samsungQuotes.forEach(System.out::println);

            System.out.println("=== 2) 코스피 지수(jcode=1) 일별 시세 (/stock/m002/hist_info) ===");
            var kospi = new StockInfo("1", "코스피", "KOSPI", "INDEX");
            List<DailyQuote> kospiQuotes = client.dailyQuotes(kospi, from, to);
            kospiQuotes.forEach(System.out::println);

            System.out.println("=== 3) KODEX 200(069500) ETF NAV — F15301 값 확인 ===");
            var kodex200 = new StockInfo("069500", "KODEX 200", "KOSPI", "ETF");
            List<NavQuote> navQuotes = client.etfNav(kodex200, from, to);
            navQuotes.forEach(System.out::println);

            System.out.println("=== 4) 존재하지 않는 종목코드(999999) — 빈 결과 처리 확인 ===");
            var ghost = new StockInfo("999999", "없는종목", "KOSPI", "STOCK");
            List<DailyQuote> ghostQuotes = client.dailyQuotes(ghost, from, to);
            System.out.println("결과 개수: " + ghostQuotes.size() + " (빈 리스트여야 정상)");
        };
    }
}
