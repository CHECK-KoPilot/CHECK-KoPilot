package com.koscom.kopilot.checkapi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Component
public class StockMasterLoader implements CommandLineRunner {

    private final JdbcTemplate jdbc;

    public StockMasterLoader(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private static final Logger log = LoggerFactory.getLogger(StockMasterLoader.class);

    private static final int BATCH_SIZE = 500;

    private static final String UPSERT = """
            INSERT INTO stock_master(code,name,market,type) VALUES (?,?,?,?)
            ON DUPLICATE KEY UPDATE name=VALUES(name), market=VALUES(market), type=VALUES(type)""";

    /**
     * csv가 종목 마스터의 원천이다. 기동할 때마다 upsert 해서 csv 수정이 기존 DB에도 반영되게 한다
     * (예전 seed가 남아 코드·시장 값이 어긋나는 사고를 막는다 — 실제로 엘앤에프의 시장이 이렇게 바로잡혔다).
     *
     * <p>csv는 {@code code_info} 계열 엔드포인트에서 받은 전체 상장 목록이라 4천 행대다.
     * 행별 update로는 기동이 눈에 띄게 느려져 batch로 넣는다.
     */
    @Override
    public void run(String... args) throws Exception {
        List<Object[]> batch = new ArrayList<>(BATCH_SIZE);
        int total = 0;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                new ClassPathResource("stock-master.csv").getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] f = line.split(",");
                if (f.length < 4) {
                    log.warn("stock-master.csv 형식 오류로 건너뛴다: {}", line);
                    continue;
                }
                batch.add(new Object[] {f[0].trim(), f[1].trim(), f[2].trim(), f[3].trim()});
                if (batch.size() == BATCH_SIZE) {
                    jdbc.batchUpdate(UPSERT, batch);
                    total += batch.size();
                    batch.clear();
                }
            }
        }
        if (!batch.isEmpty()) {
            jdbc.batchUpdate(UPSERT, batch);
            total += batch.size();
        }
        log.info("종목 마스터 {}건 적재", total);
    }
}
