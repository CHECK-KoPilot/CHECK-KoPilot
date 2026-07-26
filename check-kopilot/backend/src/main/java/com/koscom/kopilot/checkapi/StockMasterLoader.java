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
import java.util.function.Function;

@Component
public class StockMasterLoader implements CommandLineRunner {

    private final JdbcTemplate jdbc;

    public StockMasterLoader(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private static final Logger log = LoggerFactory.getLogger(StockMasterLoader.class);

    private static final int BATCH_SIZE = 500;

    private static final String UPSERT = """
            INSERT INTO stock_master(code,name,market,type) VALUES (?,?,?,?)
            ON DUPLICATE KEY UPDATE name=VALUES(name), market=VALUES(market), type=VALUES(type)""";

    /** 마스터에 없는 코드나 이름과 똑같은 별칭은 넣지 않는다 — 쓸모없거나 조회 실패를 부른다 */
    private static final String ALIAS_UPSERT = """
            INSERT INTO stock_alias(alias, code)
            SELECT ?, m.code FROM stock_master m WHERE m.code = ? AND m.name <> ?
            ON DUPLICATE KEY UPDATE code=VALUES(code)""";

    /**
     * csv가 종목 마스터의 원천이다. 기동할 때마다 upsert 해서 csv 수정이 기존 DB에도 반영되게 한다
     * (예전 seed가 남아 코드·시장 값이 어긋나는 사고를 막는다 — 실제로 엘앤에프의 시장이 이렇게 바로잡혔다).
     *
     * <p>csv는 {@code code_info} 계열 엔드포인트에서 받은 전체 상장 목록이라 4천 행대다.
     * 행별 update로는 기동이 눈에 띄게 느려져 batch로 넣는다.
     */
    @Override
    public void run(String... args) throws Exception {
        log.info("종목 마스터 {}건, 별칭 {}건 적재", loadMaster(), loadAliases());
    }

    private int loadMaster() throws Exception {
        return load("stock-master.csv", UPSERT, f -> f.length >= 4
                ? new Object[] {f[0].trim(), f[1].trim(), f[2].trim(), f[3].trim()} : null);
    }

    /**
     * 별칭은 마스터에 실제로 있는 코드만 넣는다. 상장폐지·합병으로 사라진 코드를 남겨두면
     * 검색은 성공하는데 조회가 빈 결과로 떨어져 원인을 찾기 어려워진다.
     */
    private int loadAliases() throws Exception {
        jdbc.update("DELETE FROM stock_alias");   // csv가 원천 — 지운 별칭이 DB에 남지 않게 한다
        return load("stock-aliases.csv", ALIAS_UPSERT, f -> f.length >= 2
                ? new Object[] {f[0].trim(), f[1].trim(), f[0].trim()} : null);
    }

    private int load(String resource, String sql, Function<String[], Object[]> mapper) throws Exception {
        List<Object[]> batch = new ArrayList<>(BATCH_SIZE);
        int total = 0;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                new ClassPathResource(resource).getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) continue;
                Object[] row = mapper.apply(line.split(","));
                if (row == null) {
                    log.warn("{} 형식 오류로 건너뛴다: {}", resource, line);
                    continue;
                }
                batch.add(row);
                if (batch.size() == BATCH_SIZE) {
                    jdbc.batchUpdate(sql, batch);
                    total += batch.size();
                    batch.clear();
                }
            }
        }
        if (!batch.isEmpty()) {
            jdbc.batchUpdate(sql, batch);
            total += batch.size();
        }
        return total;
    }
}
