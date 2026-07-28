package com.koscom.kopilot.checkapi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

@Component
public class StockMasterLoader implements CommandLineRunner {

    /** csv가 재생성되는 산출물이라 값 자체를 검증한다 — 필드가 밀리면 호출 경로가 어긋난다 */
    private static final Set<String> MARKETS = Set.of("KOSPI", "KOSDAQ");
    private static final Set<String> TYPES = Set.of("STOCK", "ETF", "ETN", "INDEX");

    private final JdbcTemplate jdbc;
    private final PlatformTransactionManager transactions;

    public StockMasterLoader(JdbcTemplate jdbc, PlatformTransactionManager transactions) {
        this.jdbc = jdbc;
        this.transactions = transactions;
    }

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

    /** 별칭과 같은 이유로 마스터에 실재하는 코드만 넣는다 — 상장폐지된 코드의 가중치는 죽은 값이다 */
    private static final String PRIORITY_UPSERT = """
            INSERT INTO stock_priority(code, priority)
            SELECT m.code, ? FROM stock_master m WHERE m.code = ?
            ON DUPLICATE KEY UPDATE priority=VALUES(priority)""";

    /**
     * csv가 종목 마스터의 원천이다. 기동할 때마다 upsert 해서 csv 수정이 기존 DB에도 반영되게 한다
     * (예전 seed가 남아 코드·시장 값이 어긋나는 사고를 막는다 — 실제로 엘앤에프의 시장이 이렇게 바로잡혔다).
     *
     * <p>csv는 {@code code_info} 계열 엔드포인트에서 받은 전체 상장 목록이라 4천 행대다.
     * 행별 update로는 기동이 눈에 띄게 느려져 batch로 넣는다.
     *
     * <p>별칭 INSERT는 {@code stock_master}에 행이 있어야 성립하므로 마스터를 먼저 적재한다.</p>
     */
    @Override
    public void run(String... args) throws Exception {
        int master = loadMaster();
        int aliases = loadAliases();
        int priorities = loadPriorities();
        log.info("종목 마스터 {}건, 별칭 {}건, 대표종목 가중치 {}건 적재", master, aliases, priorities);
    }

    private int loadMaster() throws Exception {
        return load("stock-master.csv", UPSERT, this::masterRow).inserted();
    }

    /**
     * 별칭은 마스터에 실제로 있는 코드만 넣는다. 상장폐지·합병으로 사라진 코드를 남겨두면
     * 검색은 성공하는데 조회가 빈 결과로 떨어져 원인을 찾기 어려워진다.
     *
     * <p>DELETE 후 재적재를 한 트랜잭션으로 묶는다 — 중간에 실패하면 별칭 테이블이 빈 채로 남고
     * 앱은 정상 기동해서 "네이버"·"코스피200" 같은 경로만 조용히 죽는다.
     */
    private int loadAliases() {
        return new TransactionTemplate(transactions).execute(status -> {
            jdbc.update("DELETE FROM stock_alias");   // csv가 원천 — 지운 별칭이 DB에 남지 않게 한다
            try {
                LoadResult result = load("stock-aliases.csv", ALIAS_UPSERT, f -> f.length >= 2
                        ? new Object[] {f[0].trim(), f[1].trim(), f[0].trim()} : null);
                if (result.inserted() < result.read()) {
                    // 마스터에 없는 코드거나 이름과 똑같은 별칭 — 조용히 사라지면 오타를 영영 못 찾는다
                    log.warn("별칭 {}줄 중 {}건만 적재됐다 — 마스터에 없는 코드나 이름과 동일한 별칭이 걸러졌다",
                            result.read(), result.inserted());
                }
                return result.inserted();
            } catch (Exception e) {
                throw new IllegalStateException("별칭 적재 실패", e);
            }
        });
    }

    /**
     * 되묻기 후보 정렬용 대표종목 가중치. 별칭과 같은 이유로 DELETE 후 재적재를 한 트랜잭션으로 묶는다 —
     * csv에서 뺀 종목의 가중치가 DB에 남으면 후보 순서가 csv와 어긋나고, 그건 화면을 봐야만 드러난다.
     */
    private int loadPriorities() {
        return new TransactionTemplate(transactions).execute(status -> {
            jdbc.update("DELETE FROM stock_priority");   // csv가 원천
            try {
                LoadResult result = load("stock-priority.csv", PRIORITY_UPSERT, this::priorityRow);
                if (result.inserted() < result.read()) {
                    log.warn("대표종목 {}줄 중 {}건만 적재됐다 — 마스터에 없는 코드가 걸러졌다",
                            result.read(), result.inserted());
                }
                return result.inserted();
            } catch (Exception e) {
                throw new IllegalStateException("대표종목 가중치 적재 실패", e);
            }
        });
    }

    /** {@code code,priority,종목명} — 3번째 필드는 사람이 읽기 위한 주석이라 읽지 않는다. */
    private Object[] priorityRow(String[] f) {
        if (f.length < 2) return null;
        try {
            return new Object[] {Integer.parseInt(f[1].trim()), f[0].trim()};   // UPSERT의 ?,? 순서
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Object[] masterRow(String[] f) {
        if (f.length != 4) return null;
        String market = f[2].trim();
        String type = f[3].trim();
        // csv는 code_info에서 재생성되는 산출물이다. 이름에 쉼표가 섞여 필드가 밀리면
        // market에 이름 조각이 들어가고, 그대로 통과하면 잘못된 엔드포인트로 호출이 나간다.
        if (!MARKETS.contains(market) || !TYPES.contains(type)) return null;
        return new Object[] {f[0].trim(), f[1].trim(), market, type};
    }

    /** @param read csv에서 읽어들인 유효 줄 수, @param inserted DB가 실제로 반영한 행 수 */
    private record LoadResult(int read, int inserted) {}

    private LoadResult load(String resource, String sql, Function<String[], Object[]> mapper) throws Exception {
        List<Object[]> batch = new ArrayList<>(BATCH_SIZE);
        int read = 0;
        int inserted = 0;
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
                read++;
                batch.add(row);
                if (batch.size() == BATCH_SIZE) {
                    inserted += affected(jdbc.batchUpdate(sql, batch));
                    batch.clear();
                }
            }
        }
        if (!batch.isEmpty()) {
            inserted += affected(jdbc.batchUpdate(sql, batch));
        }
        return new LoadResult(read, inserted);
    }

    /** upsert는 갱신 시 2를 돌려주기도 하므로 "0이 아니면 반영됨"으로 센다 */
    private static int affected(int[] counts) {
        int applied = 0;
        for (int c : counts) {
            if (c != 0) applied++;
        }
        return applied;
    }
}
