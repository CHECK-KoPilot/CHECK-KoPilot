package com.koscom.kopilot.checkapi;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

@Component
public class StockMasterLoader implements CommandLineRunner {

    private final JdbcTemplate jdbc;

    public StockMasterLoader(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /**
     * csv가 종목 마스터의 원천이다. 기동할 때마다 upsert 해서 csv 수정이 기존 DB에도 반영되게 한다
     * (데모 풀 규모라 비용이 없고, 예전 seed가 남아 코드·시장 값이 어긋나는 사고를 막는다).
     *
     * <p>csv를 {@code code_info} 엔드포인트로 채워 수천 행이 되면(ETF만 1,150건) 행별 update를
     * {@code jdbc.batchUpdate}로 바꾼다 — 그때까지는 13행이라 차이가 없다.
     */
    @Override
    public void run(String... args) throws Exception {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                new ClassPathResource("stock-master.csv").getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] f = line.split(",");
                jdbc.update("""
                        INSERT INTO stock_master(code,name,market,type) VALUES (?,?,?,?)
                        ON DUPLICATE KEY UPDATE name=VALUES(name), market=VALUES(market), type=VALUES(type)""",
                        f[0].trim(), f[1].trim(), f[2].trim(), f[3].trim());
            }
        }
    }
}
