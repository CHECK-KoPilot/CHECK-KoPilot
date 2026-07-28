# 단축키 프리셋 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 종목·지표·기간을 묶어 `Ctrl+Shift+<키>`에 걸어 두고, 키를 누르면 그 질문이 즉시 채팅으로 전송되는 프리셋 기능을 만든다.

**Architecture:** 백엔드는 새 패키지 `com.koscom.kopilot.shortcut`(REST + JDBC 저장소)과 조회용 컨트롤러 2개(`/api/catalog`, `/api/stocks`)를 추가한다. 지표 메타(라벨·프롬프트 템플릿)는 `MetricExecutor`의 default 메서드 `presetSpec()`에서 나오므로 카탈로그의 단일 출처가 유지된다. 프론트엔드는 저장된 `prompt` 문자열을 기존 `ask()`에 그대로 넣기만 하므로 전송 경로가 하나로 유지된다.

**Tech Stack:** Spring Boot 3.5.3 / Java 21 / JdbcTemplate / MySQL 8 · React 19 / Vite / Tailwind 4 / vitest + @testing-library/react

**설계 스펙:** `docs/superpowers/specs/2026-07-28-shortcut-presets-design.md`

## Global Constraints

- 브랜치는 이미 `feat/shortcut-presets`. `main`에 직접 push 금지.
- 커밋은 Conventional Commits, type은 영문 / 설명은 한국어 명사형·마침표 없음. **AI 도구 트레일러(`Co-Authored-By` 등) 금지.**
- 모든 수치 계산은 백엔드 Java가 한다. 이 기능은 계산을 하지 않는다 — 문구를 저장·전송할 뿐이다.
- 투자 판단·권유·전망 문구를 생성하지 않는다. 프롬프트 템플릿도 마찬가지다("사야 할까" 류 금지).
- 백엔드 테스트는 `@SpringBootTest`라 **MySQL(3307)·Redis(6379)가 떠 있어야 한다**: `cd check-kopilot && docker compose up -d`.
- 백엔드 테스트 프로파일은 `@ActiveProfiles("fixture")`를 쓴다(실 CHECK API 호출 금지).
- 키 조합 정규화 문자열은 소문자 `ctrl+shift+<숫자|영문>` 하나뿐이다. mac의 `⌘`도 저장은 `ctrl`로 한다.
- 종목 표기: 칩·`targets` 컬럼은 `삼성전자(005930)` 형식, 프롬프트 본문에는 **종목명만** 넣는다.
- 기간 코드는 `1M | 3M | 6M | 1Y` 넷뿐이고, 한글 표현 매핑은 프론트 `promptTemplate.js`가 갖는다.
- 명령 실행 위치: 백엔드는 `check-kopilot/backend`, 프론트는 `check-kopilot/frontend`.

## File Structure

**백엔드 (생성)**

| 파일 | 책임 |
|---|---|
| `catalog/PresetSpec.java` | 지표의 단축키 폼 메타(라벨·템플릿·대상 개수 범위) |
| `catalog/CatalogController.java` | `GET /api/catalog` |
| `checkapi/StockSearchController.java` | `GET /api/stocks` |
| `shortcut/Shortcut.java` | DB 행 1개에 대응하는 레코드 |
| `shortcut/JdbcShortcutStore.java` | shortcut 테이블 CRUD |
| `shortcut/ShortcutController.java` | REST 4개 + 검증 + 에러 매핑 |

**백엔드 (수정)**: `catalog/MetricExecutor.java`(default 메서드 1개), 실행기 7종(각 메서드 1개), `resources/schema.sql`(테이블 1개)

**프론트 (생성)**

| 파일 | 책임 |
|---|---|
| `lib/uuid.js` | UUID 생성(폴백 포함) — `session.js`에서 옮겨 온다 |
| `lib/deviceId.js` | `kopilot.deviceId` 발급·보관 |
| `lib/keyCombo.js` | keydown → 정규화 문자열, 표시 포맷 |
| `lib/promptTemplate.js` | 기간 표 + `{targets}`/`{period}` 치환 |
| `lib/shortcutsApi.js` | REST 호출 6종 |
| `hooks/useShortcuts.js` | 목록 상태 + 전역 keydown 바인딩 |
| `components/shortcuts/StockPicker.jsx` | 자동완성 칩 입력 |
| `components/shortcuts/KeyComboInput.jsx` | 키 캡처 인풋 |
| `components/shortcuts/ShortcutFormModal.jsx` | 저장 폼 |
| `components/shortcuts/ShortcutMenu.jsx` | 헤더 버튼 + 드롭다운 |

**프론트 (수정)**: `lib/session.js`, `components/layout/GlobalHeader.jsx`, `components/layout/AppLayout.jsx`, `pages/ChatPage.jsx`

---

### Task 1: 카탈로그 메타 노출 (`PresetSpec` + `GET /api/catalog`)

**Files:**
- Create: `backend/src/main/java/com/koscom/kopilot/catalog/PresetSpec.java`
- Create: `backend/src/main/java/com/koscom/kopilot/catalog/CatalogController.java`
- Modify: `backend/src/main/java/com/koscom/kopilot/catalog/MetricExecutor.java`
- Modify: 실행기 7종 (`ReturnGapExecutor`, `VolatilityExecutor`, `CumulativeReturnExecutor`, `ReturnRankingExecutor`, `MaDisparityExecutor`, `PeriodSummaryExecutor`, `NavDisparityExecutor`)
- Test: `backend/src/test/java/com/koscom/kopilot/catalog/CatalogControllerTest.java`

**Interfaces:**
- Consumes: `CatalogService.all()` → `List<MetricExecutor>` (기존)
- Produces: `PresetSpec(String label, String promptTemplate, int minTargets, int maxTargets)`, `MetricExecutor.presetSpec()`, `CatalogController.CatalogItem(String toolName, String label, String description, String promptTemplate, int minTargets, int maxTargets)`

- [ ] **Step 1: 실패하는 테스트 작성**

`backend/src/test/java/com/koscom/kopilot/catalog/CatalogControllerTest.java`

```java
package com.koscom.kopilot.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("fixture")
class CatalogControllerTest {

    @Autowired MockMvc mvc;
    @Autowired CatalogService catalog;

    @Test
    void listsEveryExecutorWithPresetMeta() throws Exception {
        String body = mvc.perform(get("/api/catalog"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        JsonNode items = new ObjectMapper().readTree(body);
        assertThat(items.size()).isEqualTo(catalog.all().size());

        // 라벨·템플릿이 비면 단축키 폼이 빈 드롭다운을 그린다 — 계약으로 못 박는다
        for (JsonNode item : items) {
            assertThat(item.path("label").asText()).isNotBlank();
            assertThat(item.path("promptTemplate").asText()).contains("{targets}");
            assertThat(item.path("minTargets").asInt()).isGreaterThanOrEqualTo(1);
            assertThat(item.path("maxTargets").asInt())
                    .isGreaterThanOrEqualTo(item.path("minTargets").asInt());
        }
    }

    @Test
    void returnGapTakesExactlyTwoTargets() throws Exception {
        String body = mvc.perform(get("/api/catalog"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        JsonNode gap = StreamSupport.stream(new ObjectMapper().readTree(body).spliterator(), false)
                .filter(n -> "return_gap".equals(n.path("toolName").asText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("return_gap이 카탈로그에 없다: " + body));

        assertThat(gap.path("minTargets").asInt()).isEqualTo(2);
        assertThat(gap.path("maxTargets").asInt()).isEqualTo(2);
    }
}
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

```bash
cd check-kopilot && docker compose up -d
cd backend && ./gradlew test --tests '*CatalogControllerTest'
```
Expected: 컴파일은 되지만 두 테스트 모두 **FAIL** — `/api/catalog`가 404를 돌려준다.

- [ ] **Step 3: `PresetSpec` 레코드 작성**

`backend/src/main/java/com/koscom/kopilot/catalog/PresetSpec.java`

```java
package com.koscom.kopilot.catalog;

/**
 * 단축키 폼이 지표를 고르고 문장을 만들 때 쓰는 메타.
 *
 * {@code promptTemplate}의 치환 토큰은 {targets}·{period} 둘뿐이고,
 * {period}가 없으면 폼이 기간 셀렉트를 숨긴다 — 별도 플래그를 두지 않는다.
 */
public record PresetSpec(String label, String promptTemplate, int minTargets, int maxTargets) {}
```

- [ ] **Step 4: `MetricExecutor`에 default 메서드 추가**

`MetricExecutor.java`의 `MetricResult execute(JsonNode args);` 아래에 붙인다.

```java
    /** 단축키 폼에 노출할 메타. null이면 폼에 나오지 않는다(새 실행기가 잊어도 컴파일은 깨지지 않는다). */
    default PresetSpec presetSpec() { return null; }
```

- [ ] **Step 5: 실행기 7종에 `presetSpec()` 구현**

각 클래스의 `description()` 바로 아래에 넣는다.

```java
// ReturnGapExecutor
    @Override public PresetSpec presetSpec() {
        return new PresetSpec("수익률 갭 비교", "{targets}의 {period} 수익률 갭을 비교해줘", 2, 2);
    }

// VolatilityExecutor
    @Override public PresetSpec presetSpec() {
        return new PresetSpec("변동성", "{targets}의 {period} 변동성을 계산해줘", 1, 5);
    }

// CumulativeReturnExecutor
    @Override public PresetSpec presetSpec() {
        return new PresetSpec("누적수익률 추이", "{targets}의 {period} 누적수익률을 차트로 보여줘", 1, 1);
    }

// ReturnRankingExecutor
    @Override public PresetSpec presetSpec() {
        return new PresetSpec("수익률 순위", "{targets}의 {period} 수익률 순위를 매겨줘", 2, 10);
    }

// MaDisparityExecutor — 기간이 아니라 기준일을 받는 지표라 {period}를 넣지 않는다
    @Override public PresetSpec presetSpec() {
        return new PresetSpec("이동평균 이격도", "{targets}의 20일 이동평균 이격도를 알려줘", 1, 1);
    }

// PeriodSummaryExecutor
    @Override public PresetSpec presetSpec() {
        return new PresetSpec("기간 시세 요약", "{targets}의 {period} 최고가·최저가·수익률을 알려줘", 1, 1);
    }

// NavDisparityExecutor — "지금 얼마나 벌어져 있나"를 묻는 지표라 기간이 없다
    @Override public PresetSpec presetSpec() {
        return new PresetSpec("ETF 괴리율", "{targets}의 괴리율을 알려줘", 1, 1);
    }
```

- [ ] **Step 6: `CatalogController` 작성**

`backend/src/main/java/com/koscom/kopilot/catalog/CatalogController.java`

```java
package com.koscom.kopilot.catalog;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 단축키 폼이 지표 목록을 그릴 때 쓴다. 카탈로그의 단일 출처는 실행기다. */
@RestController
public class CatalogController {

    public record CatalogItem(String toolName, String label, String description,
                              String promptTemplate, int minTargets, int maxTargets) {}

    private final CatalogService catalog;

    public CatalogController(CatalogService catalog) { this.catalog = catalog; }

    @GetMapping("/api/catalog")
    public List<CatalogItem> list() {
        return catalog.all().stream()
                .filter(executor -> executor.presetSpec() != null)
                .map(executor -> {
                    PresetSpec preset = executor.presetSpec();
                    return new CatalogItem(executor.toolName(), preset.label(), executor.description(),
                            preset.promptTemplate(), preset.minTargets(), preset.maxTargets());
                })
                .toList();
    }
}
```

- [ ] **Step 7: 테스트 통과 확인**

Run: `cd check-kopilot/backend && ./gradlew test --tests '*CatalogControllerTest'`
Expected: PASS (2 tests)

- [ ] **Step 8: 커밋**

```bash
git add check-kopilot/backend/src/main/java/com/koscom/kopilot/catalog \
        check-kopilot/backend/src/test/java/com/koscom/kopilot/catalog/CatalogControllerTest.java
git commit -m "feat(backend): 지표 카탈로그 조회 API 추가"
```

---

### Task 2: 종목 자동완성 API (`GET /api/stocks`)

**Files:**
- Create: `backend/src/main/java/com/koscom/kopilot/checkapi/StockSearchController.java`
- Test: `backend/src/test/java/com/koscom/kopilot/checkapi/StockSearchControllerTest.java`

**Interfaces:**
- Consumes: `StockResolver.search(String name)` → `List<StockInfo>` (기존), `StockInfo(String code, String name, String market, String type)`
- Produces: `GET /api/stocks?q=&limit=` → `StockInfo[]` JSON

- [ ] **Step 1: 실패하는 테스트 작성**

`backend/src/test/java/com/koscom/kopilot/checkapi/StockSearchControllerTest.java`

```java
package com.koscom.kopilot.checkapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("fixture")
class StockSearchControllerTest {

    @Autowired MockMvc mvc;

    private JsonNode search(String query, String limit) throws Exception {
        String body = mvc.perform(get("/api/stocks").param("q", query).param("limit", limit))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return new ObjectMapper().readTree(body);
    }

    @Test
    void partialName_returnsCandidatesWithCode() throws Exception {
        JsonNode results = search("삼성", "8");

        assertThat(StreamSupport.stream(results.spliterator(), false)
                .anyMatch(n -> "005930".equals(n.path("code").asText()))).isTrue();
        assertThat(results.get(0).path("name").asText()).isNotBlank();
        assertThat(results.get(0).path("market").asText()).isNotBlank();
    }

    @Test
    void limitIsRespected() throws Exception {
        assertThat(search("삼성", "3").size()).isLessThanOrEqualTo(3);
    }

    /** 1자 질의는 마스터 4천 행을 통째로 훑게 만든다 — 자동완성이 시작되기 전에 잘라낸다. */
    @Test
    void singleCharQuery_returnsEmpty() throws Exception {
        assertThat(search("삼", "8").size()).isZero();
    }
}
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

Run: `cd check-kopilot/backend && ./gradlew test --tests '*StockSearchControllerTest'`
Expected: FAIL — 404 (핸들러 없음)

- [ ] **Step 3: 컨트롤러 작성**

`backend/src/main/java/com/koscom/kopilot/checkapi/StockSearchController.java`

```java
package com.koscom.kopilot.checkapi;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 단축키 폼의 종목 자동완성. 되묻기와 같은 검색기를 쓴다 — 결과가 갈리면 안 된다. */
@RestController
public class StockSearchController {

    private static final int MIN_QUERY_LENGTH = 2;
    private static final int MAX_LIMIT = 20;

    private final StockResolver stocks;

    public StockSearchController(StockResolver stocks) { this.stocks = stocks; }

    @GetMapping("/api/stocks")
    public List<StockInfo> search(@RequestParam("q") String q,
                                  @RequestParam(value = "limit", defaultValue = "8") int limit) {
        String query = q == null ? "" : q.trim();
        if (query.length() < MIN_QUERY_LENGTH) return List.of();
        return stocks.search(query).stream()
                .limit(Math.clamp(limit, 1, MAX_LIMIT))
                .toList();
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd check-kopilot/backend && ./gradlew test --tests '*StockSearchControllerTest'`
Expected: PASS (3 tests)

- [ ] **Step 5: 커밋**

```bash
git add check-kopilot/backend/src/main/java/com/koscom/kopilot/checkapi/StockSearchController.java \
        check-kopilot/backend/src/test/java/com/koscom/kopilot/checkapi/StockSearchControllerTest.java
git commit -m "feat(backend): 종목 자동완성 검색 API 추가"
```

---

### Task 3: shortcut 테이블과 저장소

**Files:**
- Modify: `backend/src/main/resources/schema.sql`
- Create: `backend/src/main/java/com/koscom/kopilot/shortcut/Shortcut.java`
- Create: `backend/src/main/java/com/koscom/kopilot/shortcut/JdbcShortcutStore.java`
- Test: `backend/src/test/java/com/koscom/kopilot/shortcut/JdbcShortcutStoreTest.java`

**Interfaces:**
- Produces: `Shortcut(String id, String deviceId, String keyCombo, String toolName, String targets, String period, String prompt)`, `JdbcShortcutStore.findByDevice(String)` → `List<Shortcut>`, `.insert(Shortcut)`, `.update(Shortcut)` → `int`, `.delete(String id, String deviceId)` → `int`

- [ ] **Step 1: 실패하는 테스트 작성**

`backend/src/test/java/com/koscom/kopilot/shortcut/JdbcShortcutStoreTest.java`

```java
package com.koscom.kopilot.shortcut;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("fixture")
class JdbcShortcutStoreTest {

    // 개발 DB에는 데모로 쌓인 행이 있을 수 있다 — 테스트 전용 기기 id로 격리한다
    private static final String DEVICE = "test-device-A";
    private static final String OTHER_DEVICE = "test-device-B";

    @Autowired JdbcShortcutStore store;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM shortcut WHERE device_id IN (?, ?)", DEVICE, OTHER_DEVICE);
    }

    private Shortcut sample(String device, String combo) {
        return new Shortcut(UUID.randomUUID().toString(), device, combo, "return_gap",
                "삼성전자(005930),SK하이닉스(000660)", "3M",
                "삼성전자와 SK하이닉스의 최근 3개월 수익률 갭을 비교해줘");
    }

    @Test
    void insertedShortcut_isFoundByItsDevice() {
        store.insert(sample(DEVICE, "ctrl+shift+1"));

        List<Shortcut> found = store.findByDevice(DEVICE);

        assertThat(found).hasSize(1);
        assertThat(found.get(0).keyCombo()).isEqualTo("ctrl+shift+1");
        assertThat(found.get(0).prompt()).contains("수익률 갭");
    }

    /** 기기별로 갈리지 않으면 남의 단축키가 내 화면에 뜬다 */
    @Test
    void otherDeviceShortcuts_areNotVisible() {
        store.insert(sample(OTHER_DEVICE, "ctrl+shift+1"));

        assertThat(store.findByDevice(DEVICE)).isEmpty();
    }

    /** 같은 기기에서 같은 키를 두 번 쓰면 어느 쪽이 발사될지 정해지지 않는다 — DB가 막는다 */
    @Test
    void sameDeviceSameCombo_isRejected() {
        store.insert(sample(DEVICE, "ctrl+shift+1"));

        assertThatThrownBy(() -> store.insert(sample(DEVICE, "ctrl+shift+1")))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void differentDevicesMayShareCombo() {
        store.insert(sample(DEVICE, "ctrl+shift+1"));

        assertThatCode(() -> store.insert(sample(OTHER_DEVICE, "ctrl+shift+1")))
                .doesNotThrowAnyException();
    }

    @Test
    void update_changesPromptOfOwnShortcut() {
        Shortcut saved = sample(DEVICE, "ctrl+shift+2");
        store.insert(saved);

        int changed = store.update(new Shortcut(saved.id(), DEVICE, "ctrl+shift+3", "volatility",
                "삼성전자(005930)", "1M", "삼성전자의 최근 1개월 변동성을 계산해줘"));

        assertThat(changed).isEqualTo(1);
        assertThat(store.findByDevice(DEVICE).get(0).toolName()).isEqualTo("volatility");
        assertThat(store.findByDevice(DEVICE).get(0).keyCombo()).isEqualTo("ctrl+shift+3");
    }

    @Test
    void update_ofAnotherDeviceShortcut_changesNothing() {
        Shortcut saved = sample(OTHER_DEVICE, "ctrl+shift+4");
        store.insert(saved);

        int changed = store.update(new Shortcut(saved.id(), DEVICE, "ctrl+shift+4", "volatility",
                "삼성전자(005930)", "1M", "삼성전자의 최근 1개월 변동성을 계산해줘"));

        assertThat(changed).isZero();
    }

    @Test
    void delete_removesOnlyOwnShortcut() {
        Shortcut mine = sample(DEVICE, "ctrl+shift+5");
        store.insert(mine);

        assertThat(store.delete(mine.id(), OTHER_DEVICE)).isZero();
        assertThat(store.delete(mine.id(), DEVICE)).isEqualTo(1);
        assertThat(store.findByDevice(DEVICE)).isEmpty();
    }

    @Test
    void periodMayBeNull() {
        store.insert(new Shortcut(UUID.randomUUID().toString(), DEVICE, "ctrl+shift+6",
                "nav_disparity", "KODEX 200(069500)", null, "KODEX 200의 괴리율을 알려줘"));

        assertThat(store.findByDevice(DEVICE).get(0).period()).isNull();
    }
}
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

Run: `cd check-kopilot/backend && ./gradlew test --tests '*JdbcShortcutStoreTest'`
Expected: 컴파일 실패 — `Shortcut`, `JdbcShortcutStore` 없음

- [ ] **Step 3: 테이블 추가**

`backend/src/main/resources/schema.sql` 맨 아래에 붙인다.

```sql
-- 단축키 프리셋. prompt가 전송의 단일 진실이고, 나머지 컬럼은 폼 재편집용 메타다.
CREATE TABLE IF NOT EXISTS shortcut (
    id         CHAR(36)     NOT NULL,
    device_id  VARCHAR(64)  NOT NULL,
    key_combo  VARCHAR(40)  NOT NULL,   -- 정규화 문자열 "ctrl+shift+1"
    tool_name  VARCHAR(60)  NOT NULL,
    targets    VARCHAR(255) NOT NULL,   -- "삼성전자(005930),SK하이닉스(000660)"
    period     VARCHAR(20)  NULL,       -- 1M | 3M | 6M | 1Y (기간 없는 지표는 NULL)
    prompt     VARCHAR(300) NOT NULL,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_shortcut_device_key (device_id, key_combo),
    KEY idx_shortcut_device (device_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

- [ ] **Step 4: 레코드와 저장소 작성**

`backend/src/main/java/com/koscom/kopilot/shortcut/Shortcut.java`

```java
package com.koscom.kopilot.shortcut;

/** 단축키 프리셋 1개. targets는 "이름(코드)"를 콤마로 이은 문자열, period는 없을 수 있다. */
public record Shortcut(String id, String deviceId, String keyCombo, String toolName,
                       String targets, String period, String prompt) {}
```

`backend/src/main/java/com/koscom/kopilot/shortcut/JdbcShortcutStore.java`

```java
package com.koscom.kopilot.shortcut;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class JdbcShortcutStore {

    private static final RowMapper<Shortcut> MAPPER = (rs, rowNum) -> new Shortcut(
            rs.getString("id"), rs.getString("device_id"), rs.getString("key_combo"),
            rs.getString("tool_name"), rs.getString("targets"), rs.getString("period"),
            rs.getString("prompt"));

    private final JdbcTemplate jdbc;

    public JdbcShortcutStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** 만든 순서대로. 목록이 눌린 순서와 무관하게 고정돼야 사용자가 위치를 외운다. */
    public List<Shortcut> findByDevice(String deviceId) {
        return jdbc.query("""
                SELECT * FROM shortcut WHERE device_id = ? ORDER BY created_at, id
                """, MAPPER, deviceId);
    }

    /** 키 중복은 유니크 제약이 막는다 — DuplicateKeyException이 그대로 올라간다. */
    public void insert(Shortcut s) {
        jdbc.update("""
                INSERT INTO shortcut(id, device_id, key_combo, tool_name, targets, period, prompt)
                VALUES (?,?,?,?,?,?,?)
                """, s.id(), s.deviceId(), s.keyCombo(), s.toolName(), s.targets(), s.period(), s.prompt());
    }

    /** device_id를 조건에 넣어 남의 프리셋은 아예 만나지 않게 한다. 반환값 0 = 없거나 남의 것. */
    public int update(Shortcut s) {
        return jdbc.update("""
                UPDATE shortcut SET key_combo = ?, tool_name = ?, targets = ?, period = ?, prompt = ?
                WHERE id = ? AND device_id = ?
                """, s.keyCombo(), s.toolName(), s.targets(), s.period(), s.prompt(), s.id(), s.deviceId());
    }

    public int delete(String id, String deviceId) {
        return jdbc.update("DELETE FROM shortcut WHERE id = ? AND device_id = ?", id, deviceId);
    }
}
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `cd check-kopilot/backend && ./gradlew test --tests '*JdbcShortcutStoreTest'`
Expected: PASS (8 tests)

`schema.sql`은 기동 시마다 실행되므로 별도 마이그레이션 절차가 없다. 테스트가 테이블 없음으로 실패하면 앱이 한 번도 안 뜬 것이니 `./gradlew test`가 컨텍스트를 띄우며 만든다.

- [ ] **Step 6: 커밋**

```bash
git add check-kopilot/backend/src/main/resources/schema.sql \
        check-kopilot/backend/src/main/java/com/koscom/kopilot/shortcut \
        check-kopilot/backend/src/test/java/com/koscom/kopilot/shortcut
git commit -m "feat(backend): 단축키 프리셋 테이블과 저장소 추가"
```

---

### Task 4: 단축키 REST API

**Files:**
- Create: `backend/src/main/java/com/koscom/kopilot/shortcut/ShortcutController.java`
- Test: `backend/src/test/java/com/koscom/kopilot/shortcut/ShortcutControllerTest.java`

**Interfaces:**
- Consumes: `JdbcShortcutStore` (Task 3), `CatalogService.byName(String)`/`.all()` + `MetricExecutor.presetSpec()` (Task 1)
- Produces: HTTP 계약
  - `GET /api/shortcuts` (헤더 `X-Device-Id`) → `ShortcutView[]`
  - `POST /api/shortcuts` → 201 `ShortcutView`
  - `PUT /api/shortcuts/{id}` → 200 `ShortcutView`
  - `DELETE /api/shortcuts/{id}` → 204
  - `ShortcutView(String id, String keyCombo, String toolName, List<String> targets, String period, String prompt)`
  - 에러 바디 `{"code": "...", "message": "..."}`; 중복 409 `KEY_TAKEN`, 검증 400, 없음/남의 것 404

- [ ] **Step 1: 실패하는 테스트 작성**

`backend/src/test/java/com/koscom/kopilot/shortcut/ShortcutControllerTest.java`

```java
package com.koscom.kopilot.shortcut;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("fixture")
class ShortcutControllerTest {

    private static final String DEVICE = "test-device-ctl-A";
    private static final String OTHER_DEVICE = "test-device-ctl-B";

    private static final String VALID_BODY = """
            {"keyCombo":"ctrl+shift+1","toolName":"return_gap",
             "targets":["삼성전자(005930)","SK하이닉스(000660)"],"period":"3M",
             "prompt":"삼성전자와 SK하이닉스의 최근 3개월 수익률 갭을 비교해줘"}
            """;

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM shortcut WHERE device_id IN (?, ?)", DEVICE, OTHER_DEVICE);
    }

    private String create(String device, String body) throws Exception {
        return mvc.perform(post("/api/shortcuts")
                        .header("X-Device-Id", device)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void created_thenListedForSameDevice() throws Exception {
        JsonNode created = new ObjectMapper().readTree(create(DEVICE, VALID_BODY));
        assertThat(created.path("id").asText()).isNotBlank();
        assertThat(created.path("targets").size()).isEqualTo(2);

        String list = mvc.perform(get("/api/shortcuts").header("X-Device-Id", DEVICE))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        JsonNode items = new ObjectMapper().readTree(list);
        assertThat(items.size()).isEqualTo(1);
        assertThat(items.get(0).path("prompt").asText()).contains("수익률 갭");
        assertThat(items.get(0).path("targets").get(0).asText()).isEqualTo("삼성전자(005930)");
    }

    @Test
    void otherDevice_seesNothing() throws Exception {
        create(DEVICE, VALID_BODY);

        mvc.perform(get("/api/shortcuts").header("X-Device-Id", OTHER_DEVICE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void duplicateCombo_conflicts() throws Exception {
        create(DEVICE, VALID_BODY);

        mvc.perform(post("/api/shortcuts")
                        .header("X-Device-Id", DEVICE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("KEY_TAKEN"));
    }

    @Test
    void badKeyCombo_isRejected() throws Exception {
        mvc.perform(post("/api/shortcuts")
                        .header("X-Device-Id", DEVICE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY.replace("ctrl+shift+1", "ctrl+t")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("KEY_COMBO_INVALID"));
    }

    @Test
    void unknownTool_isRejected() throws Exception {
        mvc.perform(post("/api/shortcuts")
                        .header("X-Device-Id", DEVICE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY.replace("return_gap", "moon_phase")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TOOL_UNKNOWN"));
    }

    /** return_gap은 정확히 2개다. 1개로 저장되면 단축키가 매번 되묻기로 샌다. */
    @Test
    void targetCountOutsideCatalogRange_isRejected() throws Exception {
        mvc.perform(post("/api/shortcuts")
                        .header("X-Device-Id", DEVICE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY.replace("[\"삼성전자(005930)\",\"SK하이닉스(000660)\"]",
                                "[\"삼성전자(005930)\"]")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TARGET_COUNT_INVALID"));
    }

    @Test
    void blankPrompt_isRejected() throws Exception {
        mvc.perform(post("/api/shortcuts")
                        .header("X-Device-Id", DEVICE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY.replace(
                                "삼성전자와 SK하이닉스의 최근 3개월 수익률 갭을 비교해줘", "   ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PROMPT_INVALID"));
    }

    @Test
    void update_replacesFields() throws Exception {
        String id = new ObjectMapper().readTree(create(DEVICE, VALID_BODY)).path("id").asText();

        mvc.perform(put("/api/shortcuts/" + id)
                        .header("X-Device-Id", DEVICE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"keyCombo":"ctrl+shift+9","toolName":"volatility",
                                 "targets":["삼성전자(005930)"],"period":"1M",
                                 "prompt":"삼성전자의 최근 1개월 변동성을 계산해줘"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keyCombo").value("ctrl+shift+9"))
                .andExpect(jsonPath("$.toolName").value("volatility"));
    }

    @Test
    void updatingAnotherDeviceShortcut_isNotFound() throws Exception {
        String id = new ObjectMapper().readTree(create(DEVICE, VALID_BODY)).path("id").asText();

        mvc.perform(put("/api/shortcuts/" + id)
                        .header("X-Device-Id", OTHER_DEVICE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_removesIt() throws Exception {
        String id = new ObjectMapper().readTree(create(DEVICE, VALID_BODY)).path("id").asText();

        mvc.perform(delete("/api/shortcuts/" + id).header("X-Device-Id", DEVICE))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/shortcuts").header("X-Device-Id", DEVICE))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void deletingUnknownId_isNotFound() throws Exception {
        mvc.perform(delete("/api/shortcuts/does-not-exist").header("X-Device-Id", DEVICE))
                .andExpect(status().isNotFound());
    }

    @Test
    void missingDeviceHeader_isBadRequest() throws Exception {
        mvc.perform(get("/api/shortcuts")).andExpect(status().isBadRequest());
    }
}
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

Run: `cd check-kopilot/backend && ./gradlew test --tests '*ShortcutControllerTest'`
Expected: FAIL — 대부분 404/405

- [ ] **Step 3: 컨트롤러 작성**

`backend/src/main/java/com/koscom/kopilot/shortcut/ShortcutController.java`

```java
package com.koscom.kopilot.shortcut;

import com.koscom.kopilot.catalog.CatalogService;
import com.koscom.kopilot.catalog.MetricExecutor;
import com.koscom.kopilot.catalog.PresetSpec;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 단축키 프리셋 CRUD. 소유자는 로그인이 아니라 브라우저가 발급한 X-Device-Id다.
 *
 * 계산이 없는 API지만 검증은 조인다 — 잘못 저장된 프리셋은 사용자가 키를 누를 때마다
 * 되묻기로 새고, 그 시점엔 왜 그런지 알기 어렵다.
 */
@RestController
@RequestMapping("/api/shortcuts")
public class ShortcutController {

    private static final Pattern KEY_COMBO = Pattern.compile("^ctrl\\+shift\\+[a-z0-9]$");
    private static final int MAX_PROMPT_LENGTH = 300;
    private static final int MAX_DEVICE_ID_LENGTH = 64;

    public record ShortcutRequest(String keyCombo, String toolName, List<String> targets,
                                  String period, String prompt) {}

    public record ShortcutView(String id, String keyCombo, String toolName, List<String> targets,
                               String period, String prompt) {}

    /** 상태코드와 코드값을 함께 나르는 예외. 프론트가 code로 분기한다. */
    static class ShortcutException extends RuntimeException {
        final HttpStatus status;
        final String code;
        ShortcutException(HttpStatus status, String code, String message) {
            super(message);
            this.status = status;
            this.code = code;
        }
    }

    private final JdbcShortcutStore store;
    private final CatalogService catalog;

    public ShortcutController(JdbcShortcutStore store, CatalogService catalog) {
        this.store = store;
        this.catalog = catalog;
    }

    @GetMapping
    public List<ShortcutView> list(@RequestHeader("X-Device-Id") String deviceId) {
        return store.findByDevice(requireDeviceId(deviceId)).stream().map(ShortcutController::toView).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ShortcutView create(@RequestHeader("X-Device-Id") String deviceId,
                               @RequestBody ShortcutRequest body) {
        Shortcut shortcut = validated(UUID.randomUUID().toString(), requireDeviceId(deviceId), body);
        try {
            store.insert(shortcut);
        } catch (DuplicateKeyException e) {
            throw keyTaken(shortcut.keyCombo());
        }
        return toView(shortcut);
    }

    @PutMapping("/{id}")
    public ShortcutView update(@RequestHeader("X-Device-Id") String deviceId,
                               @PathVariable String id,
                               @RequestBody ShortcutRequest body) {
        Shortcut shortcut = validated(id, requireDeviceId(deviceId), body);
        int changed;
        try {
            changed = store.update(shortcut);
        } catch (DuplicateKeyException e) {
            throw keyTaken(shortcut.keyCombo());
        }
        if (changed == 0) throw notFound();
        return toView(shortcut);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestHeader("X-Device-Id") String deviceId, @PathVariable String id) {
        if (store.delete(id, requireDeviceId(deviceId)) == 0) throw notFound();
    }

    @ExceptionHandler(ShortcutException.class)
    ResponseEntity<Map<String, String>> handle(ShortcutException e) {
        return ResponseEntity.status(e.status).body(Map.of("code", e.code, "message", e.getMessage()));
    }

    private static ShortcutException keyTaken(String combo) {
        return new ShortcutException(HttpStatus.CONFLICT, "KEY_TAKEN",
                "이미 사용 중인 키 조합입니다: " + combo);
    }

    private static ShortcutException notFound() {
        // 남의 프리셋인지 없는 프리셋인지 구분해 주지 않는다
        return new ShortcutException(HttpStatus.NOT_FOUND, "NOT_FOUND", "단축키를 찾을 수 없습니다");
    }

    private static String requireDeviceId(String deviceId) {
        if (deviceId == null || deviceId.isBlank() || deviceId.length() > MAX_DEVICE_ID_LENGTH) {
            throw new ShortcutException(HttpStatus.BAD_REQUEST, "DEVICE_ID_INVALID",
                    "X-Device-Id 헤더가 필요합니다");
        }
        return deviceId;
    }

    private Shortcut validated(String id, String deviceId, ShortcutRequest body) {
        if (body == null || body.keyCombo() == null || !KEY_COMBO.matcher(body.keyCombo()).matches()) {
            throw new ShortcutException(HttpStatus.BAD_REQUEST, "KEY_COMBO_INVALID",
                    "키 조합은 ctrl+shift+<숫자·영문> 형식이어야 합니다");
        }

        MetricExecutor executor;
        try {
            executor = catalog.byName(body.toolName());
        } catch (IllegalArgumentException e) {
            throw new ShortcutException(HttpStatus.BAD_REQUEST, "TOOL_UNKNOWN",
                    "알 수 없는 지표입니다: " + body.toolName());
        }
        PresetSpec preset = executor.presetSpec();
        if (preset == null) {
            throw new ShortcutException(HttpStatus.BAD_REQUEST, "TOOL_UNKNOWN",
                    "단축키로 만들 수 없는 지표입니다: " + body.toolName());
        }

        List<String> targets = body.targets() == null ? List.of()
                : body.targets().stream().filter(t -> t != null && !t.isBlank()).map(String::trim).toList();
        if (targets.size() < preset.minTargets() || targets.size() > preset.maxTargets()) {
            throw new ShortcutException(HttpStatus.BAD_REQUEST, "TARGET_COUNT_INVALID",
                    "%s은(는) 종목 %d~%d개가 필요합니다"
                            .formatted(preset.label(), preset.minTargets(), preset.maxTargets()));
        }

        String prompt = body.prompt() == null ? "" : body.prompt().trim();
        if (prompt.isEmpty() || prompt.length() > MAX_PROMPT_LENGTH) {
            throw new ShortcutException(HttpStatus.BAD_REQUEST, "PROMPT_INVALID",
                    "프롬프트는 1~%d자여야 합니다".formatted(MAX_PROMPT_LENGTH));
        }

        String period = body.period() == null || body.period().isBlank() ? null : body.period().trim();
        return new Shortcut(id, deviceId, body.keyCombo(), body.toolName(),
                String.join(",", targets), period, prompt);
    }

    private static ShortcutView toView(Shortcut s) {
        List<String> targets = s.targets() == null || s.targets().isBlank()
                ? List.of() : Arrays.stream(s.targets().split(",")).toList();
        return new ShortcutView(s.id(), s.keyCombo(), s.toolName(), targets, s.period(), s.prompt());
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd check-kopilot/backend && ./gradlew test --tests '*ShortcutControllerTest'`
Expected: PASS (12 tests)

- [ ] **Step 5: 백엔드 전체 테스트로 회귀 확인**

Run: `cd check-kopilot/backend && ./gradlew test`
Expected: BUILD SUCCESSFUL — 기존 테스트가 하나도 깨지지 않아야 한다. `MetricExecutor`에 default 메서드만 더했으므로 실행기 테스트는 영향이 없다.

- [ ] **Step 6: 커밋**

```bash
git add check-kopilot/backend/src/main/java/com/koscom/kopilot/shortcut/ShortcutController.java \
        check-kopilot/backend/src/test/java/com/koscom/kopilot/shortcut/ShortcutControllerTest.java
git commit -m "feat(backend): 단축키 프리셋 CRUD API 추가"
```

---

### Task 5: 프론트 순수 로직 (`uuid` · `deviceId` · `keyCombo` · `promptTemplate`)

**Files:**
- Create: `frontend/src/lib/uuid.js`
- Create: `frontend/src/lib/deviceId.js`
- Create: `frontend/src/lib/keyCombo.js`
- Create: `frontend/src/lib/promptTemplate.js`
- Modify: `frontend/src/lib/session.js` (randomUUID를 `uuid.js`에서 가져오도록)
- Test: `frontend/src/lib/__tests__/keyCombo.test.js`, `frontend/src/lib/__tests__/promptTemplate.test.js`

**Interfaces:**
- Produces:
  - `randomUUID()` → `string`
  - `getDeviceId()` → `string`
  - `comboFromEvent(event)` → `string | null` (`"ctrl+shift+1"`)
  - `formatCombo(combo, isMac)` → `string` (`"Ctrl + Shift + 1"`)
  - `PERIODS` → `[{ code, label, phrase }]`
  - `needsPeriod(template)` → `boolean`
  - `stockLabel({ name, code })` → `string` (`"삼성전자(005930)"`)
  - `nameOf(label)` → `string` (`"삼성전자(005930)"` → `"삼성전자"`)
  - `buildPrompt({ template, targetLabels, periodCode })` → `string`

- [ ] **Step 1: 실패하는 테스트 작성 (keyCombo)**

`frontend/src/lib/__tests__/keyCombo.test.js`

```js
import { describe, it, expect } from "vitest";
import { comboFromEvent, formatCombo } from "../keyCombo";

/** keydown 이벤트의 최소 형태. code를 쓰는 이유는 Shift와 함께 누르면 key가 "!"로 바뀌기 때문이다. */
function evt({ code, ctrlKey = false, metaKey = false, shiftKey = false, altKey = false }) {
  return { code, ctrlKey, metaKey, shiftKey, altKey };
}

describe("comboFromEvent", () => {
  it("Ctrl+Shift+숫자를 정규화한다", () => {
    expect(comboFromEvent(evt({ code: "Digit1", ctrlKey: true, shiftKey: true })))
      .toBe("ctrl+shift+1");
  });

  it("mac의 Cmd도 ctrl로 저장한다", () => {
    expect(comboFromEvent(evt({ code: "KeyK", metaKey: true, shiftKey: true })))
      .toBe("ctrl+shift+k");
  });

  it("Shift가 없으면 거부한다", () => {
    expect(comboFromEvent(evt({ code: "Digit1", ctrlKey: true }))).toBeNull();
  });

  it("Alt가 섞이면 거부한다", () => {
    expect(comboFromEvent(evt({ code: "Digit1", ctrlKey: true, shiftKey: true, altKey: true })))
      .toBeNull();
  });

  it("숫자·영문이 아닌 키는 거부한다", () => {
    expect(comboFromEvent(evt({ code: "Slash", ctrlKey: true, shiftKey: true }))).toBeNull();
    expect(comboFromEvent(evt({ code: "ShiftLeft", ctrlKey: true, shiftKey: true }))).toBeNull();
  });
});

describe("formatCombo", () => {
  it("윈도우 표기", () => {
    expect(formatCombo("ctrl+shift+1", false)).toBe("Ctrl + Shift + 1");
  });

  it("mac 표기", () => {
    expect(formatCombo("ctrl+shift+k", true)).toBe("⌘ + ⇧ + K");
  });
});
```

- [ ] **Step 2: 실패하는 테스트 작성 (promptTemplate)**

`frontend/src/lib/__tests__/promptTemplate.test.js`

```js
import { describe, it, expect } from "vitest";
import { buildPrompt, needsPeriod, stockLabel, nameOf, PERIODS } from "../promptTemplate";

describe("stockLabel / nameOf", () => {
  it("칩 라벨은 이름(코드) 형식이다", () => {
    expect(stockLabel({ name: "삼성전자", code: "005930" })).toBe("삼성전자(005930)");
  });

  it("이름만 다시 꺼낼 수 있다", () => {
    expect(nameOf("삼성전자(005930)")).toBe("삼성전자");
    expect(nameOf("TIGER 미국S&P500(360750)")).toBe("TIGER 미국S&P500");
    expect(nameOf("코스피")).toBe("코스피"); // 코드가 없는 표기도 그대로 통과
  });
});

describe("needsPeriod", () => {
  it("{period}가 있으면 기간이 필요하다", () => {
    expect(needsPeriod("{targets}의 {period} 변동성을 계산해줘")).toBe(true);
    expect(needsPeriod("{targets}의 괴리율을 알려줘")).toBe(false);
  });
});

describe("buildPrompt", () => {
  it("종목명만 넣고 기간을 치환한다", () => {
    expect(
      buildPrompt({
        template: "{targets}의 {period} 수익률 갭을 비교해줘",
        targetLabels: ["삼성전자(005930)", "SK하이닉스(000660)"],
        periodCode: "3M",
      })
    ).toBe("삼성전자와 SK하이닉스의 최근 3개월 수익률 갭을 비교해줘");
  });

  it("받침이 있으면 '과'로 잇는다", () => {
    expect(
      buildPrompt({
        template: "{targets}의 {period} 수익률 갭을 비교해줘",
        targetLabels: ["SK하이닉스(000660)", "삼성전자(005930)"],
        periodCode: "1M",
      })
    ).toBe("SK하이닉스와 삼성전자의 최근 1개월 수익률 갭을 비교해줘");
  });

  it("셋 이상은 쉼표로 잇는다", () => {
    expect(
      buildPrompt({
        template: "{targets}의 {period} 수익률 순위를 매겨줘",
        targetLabels: ["에코프로(086520)", "엘앤에프(066970)", "포스코퓨처엠(003670)"],
        periodCode: "6M",
      })
    ).toBe("에코프로, 엘앤에프, 포스코퓨처엠의 최근 6개월 수익률 순위를 매겨줘");
  });

  it("{period}가 없는 템플릿은 기간을 무시한다", () => {
    expect(
      buildPrompt({
        template: "{targets}의 괴리율을 알려줘",
        targetLabels: ["KODEX 200(069500)"],
        periodCode: "3M",
      })
    ).toBe("KODEX 200의 괴리율을 알려줘");
  });

  it("기간을 안 고르면 기간 표현 없이 문장이 이어진다", () => {
    expect(
      buildPrompt({
        template: "{targets}의 {period} 변동성을 계산해줘",
        targetLabels: ["삼성전자(005930)"],
        periodCode: null,
      })
    ).toBe("삼성전자의 변동성을 계산해줘");
  });
});

describe("PERIODS", () => {
  it("코드 4종을 갖는다", () => {
    expect(PERIODS.map((p) => p.code)).toEqual(["1M", "3M", "6M", "1Y"]);
  });
});
```

- [ ] **Step 3: 테스트가 실패하는지 확인**

Run: `cd check-kopilot/frontend && npm test -- keyCombo promptTemplate`
Expected: FAIL — 모듈을 찾을 수 없음

- [ ] **Step 4: `uuid.js`로 추출하고 `session.js`를 정리**

`frontend/src/lib/uuid.js` (본문은 `session.js`에 있던 함수를 그대로 옮긴다)

```js
/**
 * crypto.randomUUID()는 보안 컨텍스트(HTTPS/localhost)에서만 존재한다.
 * HTTP로 서빙되는 배포 환경에서도 id 발급이 죽지 않도록 getRandomValues 기반 폴백을 둔다.
 */
export function randomUUID() {
  if (typeof crypto !== "undefined" && crypto.randomUUID) {
    return crypto.randomUUID();
  }
  const bytes = crypto.getRandomValues(new Uint8Array(16));
  bytes[6] = (bytes[6] & 0x0f) | 0x40; // version 4
  bytes[8] = (bytes[8] & 0x3f) | 0x80; // variant
  const hex = [...bytes].map((b) => b.toString(16).padStart(2, "0"));
  return [
    hex.slice(0, 4).join(""),
    hex.slice(4, 6).join(""),
    hex.slice(6, 8).join(""),
    hex.slice(8, 10).join(""),
    hex.slice(10, 16).join(""),
  ].join("-");
}
```

`frontend/src/lib/session.js` — 파일 안의 `randomUUID` 정의(주석 포함 3~22행)를 지우고 맨 위에 import를 넣는다. 나머지 함수는 그대로 둔다.

```js
import { randomUUID } from "./uuid";

const KEY = "kopilot.sessionId";
```

- [ ] **Step 5: `deviceId.js` 작성**

`frontend/src/lib/deviceId.js`

```js
import { randomUUID } from "./uuid";

const KEY = "kopilot.deviceId";

/**
 * 단축키 프리셋의 소유자 키.
 *
 * sessionId는 "새 대화"마다 새로 발급되므로 프리셋을 묶을 수 없다.
 * 로그인이 없는 MVP에서 "이 브라우저"를 가리키는 값이 따로 필요하다.
 */
export function getDeviceId() {
  const saved = localStorage.getItem(KEY);
  if (saved) return saved;
  const fresh = randomUUID();
  localStorage.setItem(KEY, fresh);
  return fresh;
}
```

- [ ] **Step 6: `keyCombo.js` 작성**

`frontend/src/lib/keyCombo.js`

```js
/**
 * 키 조합의 정규화 형식은 "ctrl+shift+<숫자|영문>" 하나뿐이다.
 *
 * Ctrl(mac은 ⌘)+Shift로 범위를 좁힌 이유는 브라우저 예약 조합을 피하기 위해서다.
 * Ctrl+T/W/N은 탭·창을 열고, Alt+숫자는 탭을 전환한다.
 */
const DIGIT = /^Digit([0-9])$/;
const LETTER = /^Key([A-Z])$/;

/** Shift를 누르면 event.key가 "!"로 바뀌므로 물리 키인 event.code를 본다. */
function baseKey(event) {
  const code = event.code ?? "";
  const digit = DIGIT.exec(code);
  if (digit) return digit[1];
  const letter = LETTER.exec(code);
  if (letter) return letter[1].toLowerCase();
  return null;
}

/** keydown 이벤트 → "ctrl+shift+1". 허용 범위 밖이면 null. */
export function comboFromEvent(event) {
  if (!(event.ctrlKey || event.metaKey)) return null;
  if (!event.shiftKey || event.altKey) return null;
  const key = baseKey(event);
  return key ? `ctrl+shift+${key}` : null;
}

/** 사람이 읽는 표기. 저장 형식은 플랫폼과 무관하게 하나이고 표시만 갈린다. */
export function formatCombo(combo, isMac = false) {
  const key = combo.split("+").at(-1) ?? "";
  const shown = key.length === 1 ? key.toUpperCase() : key;
  return isMac ? `⌘ + ⇧ + ${shown}` : `Ctrl + Shift + ${shown}`;
}

export function isMacPlatform() {
  return typeof navigator !== "undefined" && /Mac|iPhone|iPad/.test(navigator.platform ?? "");
}
```

- [ ] **Step 7: `promptTemplate.js` 작성**

`frontend/src/lib/promptTemplate.js`

```js
export const PERIODS = [
  { code: "1M", label: "최근 1개월", phrase: "최근 1개월" },
  { code: "3M", label: "최근 3개월", phrase: "최근 3개월" },
  { code: "6M", label: "최근 6개월", phrase: "최근 6개월" },
  { code: "1Y", label: "최근 1년", phrase: "최근 1년" },
];

/** 칩·저장값 표기. 되묻기 카드가 쓰는 "이름(코드)"와 같은 형식으로 맞춘다. */
export function stockLabel({ name, code }) {
  return code ? `${name}(${code})` : name;
}

/** "삼성전자(005930)" → "삼성전자". 프롬프트 본문에는 코드를 넣지 않는다 — 문장이 읽기 나빠진다. */
export function nameOf(label) {
  return label.replace(/\([^)]*\)$/, "").trim();
}

export function needsPeriod(template) {
  return template.includes("{period}");
}

/** 한글 마지막 글자에 받침이 있는지 — "와/과"를 고르는 데만 쓴다. */
function hasFinalConsonant(text) {
  const last = text.at(-1) ?? "";
  const code = last.charCodeAt(0);
  if (code < 0xac00 || code > 0xd7a3) return false; // 한글이 아니면 "와"로 둔다
  return (code - 0xac00) % 28 !== 0;
}

function joinNames(names) {
  if (names.length === 0) return "";
  if (names.length === 1) return names[0];
  if (names.length === 2) return `${names[0]}${hasFinalConsonant(names[0]) ? "과" : "와"} ${names[1]}`;
  return names.join(", ");
}

/**
 * 템플릿 → 실제로 전송될 문장.
 *
 * 치환 후 남는 공백을 정리하는 이유는 기간을 안 고른 경우 "{period}" 자리가 비기 때문이다.
 */
export function buildPrompt({ template, targetLabels, periodCode }) {
  const phrase = PERIODS.find((p) => p.code === periodCode)?.phrase ?? "";
  return template
    .replace("{targets}", joinNames(targetLabels.map(nameOf)))
    .replace("{period}", phrase)
    .replace(/\s+/g, " ")
    .trim();
}
```

- [ ] **Step 8: 테스트 통과 확인**

Run: `cd check-kopilot/frontend && npm test`
Expected: 신규 2개 파일 PASS + 기존 테스트 전부 PASS (`session.js` 수정이 회귀를 내지 않았는지 함께 본다)

- [ ] **Step 9: 커밋**

```bash
git add check-kopilot/frontend/src/lib
git commit -m "feat(frontend): 단축키 키 조합·프롬프트 조립 유틸 추가"
```

---

### Task 6: API 클라이언트와 `useShortcuts` 훅

**Files:**
- Create: `frontend/src/lib/shortcutsApi.js`
- Create: `frontend/src/hooks/useShortcuts.js`
- Test: `frontend/src/hooks/__tests__/useShortcuts.test.jsx`

**Interfaces:**
- Consumes: `getDeviceId()` (Task 5), `comboFromEvent(event)` (Task 5)
- Produces:
  - `fetchCatalog()` → `Promise<CatalogItem[]>`
  - `searchStocks(q, signal)` → `Promise<{code,name,market,type}[]>`
  - `fetchShortcuts()` → `Promise<ShortcutView[]>`
  - `createShortcut(body)` / `updateShortcut(id, body)` → `Promise<ShortcutView>`
  - `deleteShortcut(id)` → `Promise<void>`
  - `ShortcutApiError` (필드 `code`, `status`)
  - `useShortcuts({ onTrigger, enabled })` → `{ shortcuts, loadError, reload }`

- [ ] **Step 1: 실패하는 테스트 작성**

`frontend/src/hooks/__tests__/useShortcuts.test.jsx`

```jsx
import { renderHook, waitFor, act } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { useShortcuts } from "../useShortcuts";

const SHORTCUT = {
  id: "s1",
  keyCombo: "ctrl+shift+1",
  toolName: "return_gap",
  targets: ["삼성전자(005930)", "SK하이닉스(000660)"],
  period: "3M",
  prompt: "삼성전자와 SK하이닉스의 최근 3개월 수익률 갭을 비교해줘",
};

function pressCtrlShift1({ isComposing = false } = {}) {
  const event = new KeyboardEvent("keydown", {
    code: "Digit1",
    ctrlKey: true,
    shiftKey: true,
    bubbles: true,
    cancelable: true,
  });
  // jsdom의 KeyboardEvent는 isComposing을 생성자로 못 받는다
  Object.defineProperty(event, "isComposing", { value: isComposing });
  act(() => { window.dispatchEvent(event); });
  return event;
}

beforeEach(() => {
  localStorage.clear();
  vi.stubGlobal("fetch", vi.fn(() =>
    Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve([SHORTCUT]) })
  ));
});

describe("useShortcuts", () => {
  it("마운트하면 목록을 불러온다", async () => {
    const { result } = renderHook(() => useShortcuts({ onTrigger: vi.fn(), enabled: true }));

    await waitFor(() => expect(result.current.shortcuts).toHaveLength(1));
    expect(result.current.loadError).toBe(false);
  });

  it("등록된 키를 누르면 저장된 프롬프트로 onTrigger를 부른다", async () => {
    const onTrigger = vi.fn();
    const { result } = renderHook(() => useShortcuts({ onTrigger, enabled: true }));
    await waitFor(() => expect(result.current.shortcuts).toHaveLength(1));

    const event = pressCtrlShift1();

    expect(onTrigger).toHaveBeenCalledWith(SHORTCUT.prompt);
    expect(event.defaultPrevented).toBe(true);
  });

  it("한글 입력 조합 중에는 발사하지 않는다", async () => {
    const onTrigger = vi.fn();
    const { result } = renderHook(() => useShortcuts({ onTrigger, enabled: true }));
    await waitFor(() => expect(result.current.shortcuts).toHaveLength(1));

    pressCtrlShift1({ isComposing: true });

    expect(onTrigger).not.toHaveBeenCalled();
  });

  it("enabled=false면 발사하지 않는다", async () => {
    const onTrigger = vi.fn();
    const { result } = renderHook(() => useShortcuts({ onTrigger, enabled: false }));
    await waitFor(() => expect(result.current.shortcuts).toHaveLength(1));

    pressCtrlShift1();

    expect(onTrigger).not.toHaveBeenCalled();
  });

  it("등록되지 않은 키는 브라우저 기본 동작을 막지 않는다", async () => {
    const onTrigger = vi.fn();
    const { result } = renderHook(() => useShortcuts({ onTrigger, enabled: true }));
    await waitFor(() => expect(result.current.shortcuts).toHaveLength(1));

    const event = new KeyboardEvent("keydown", {
      code: "Digit9", ctrlKey: true, shiftKey: true, bubbles: true, cancelable: true,
    });
    act(() => { window.dispatchEvent(event); });

    expect(onTrigger).not.toHaveBeenCalled();
    expect(event.defaultPrevented).toBe(false);
  });

  it("목록 로드가 실패하면 loadError를 세운다", async () => {
    vi.stubGlobal("fetch", vi.fn(() => Promise.reject(new Error("network"))));
    const { result } = renderHook(() => useShortcuts({ onTrigger: vi.fn(), enabled: true }));

    await waitFor(() => expect(result.current.loadError).toBe(true));
    expect(result.current.shortcuts).toEqual([]);
  });
});
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

Run: `cd check-kopilot/frontend && npm test -- useShortcuts`
Expected: FAIL — `../useShortcuts` 없음

- [ ] **Step 3: `shortcutsApi.js` 작성**

`frontend/src/lib/shortcutsApi.js`

```js
import { getDeviceId } from "./deviceId";

/** 서버가 내려준 code로 화면이 분기할 수 있게 상태코드와 함께 실어 나른다. */
export class ShortcutApiError extends Error {
  constructor(status, code, message) {
    super(message);
    this.status = status;
    this.code = code;
  }
}

function deviceHeaders() {
  return { "Content-Type": "application/json", "X-Device-Id": getDeviceId() };
}

async function parse(res) {
  if (res.ok) return res.status === 204 ? null : res.json();
  let body = {};
  try {
    body = await res.json();
  } catch {
    // 에러 바디가 JSON이 아닐 수 있다 — 상태코드만으로도 화면은 분기할 수 있다
  }
  throw new ShortcutApiError(res.status, body.code ?? "UNKNOWN", body.message ?? "요청에 실패했습니다");
}

export async function fetchCatalog() {
  return parse(await fetch("/api/catalog"));
}

export async function searchStocks(query, signal) {
  const q = encodeURIComponent(query);
  return parse(await fetch(`/api/stocks?q=${q}&limit=8`, { signal }));
}

export async function fetchShortcuts() {
  return parse(await fetch("/api/shortcuts", { headers: deviceHeaders() }));
}

export async function createShortcut(body) {
  return parse(await fetch("/api/shortcuts", {
    method: "POST", headers: deviceHeaders(), body: JSON.stringify(body),
  }));
}

export async function updateShortcut(id, body) {
  return parse(await fetch(`/api/shortcuts/${id}`, {
    method: "PUT", headers: deviceHeaders(), body: JSON.stringify(body),
  }));
}

export async function deleteShortcut(id) {
  return parse(await fetch(`/api/shortcuts/${id}`, {
    method: "DELETE", headers: deviceHeaders(),
  }));
}
```

- [ ] **Step 4: `useShortcuts.js` 작성**

`frontend/src/hooks/useShortcuts.js`

```js
import { useCallback, useEffect, useRef, useState } from "react";
import { fetchShortcuts } from "../lib/shortcutsApi";
import { comboFromEvent } from "../lib/keyCombo";

/**
 * 단축키 목록과 전역 키 바인딩.
 *
 * 리스너는 한 번만 붙이고 최신 값은 ref로 읽는다 — 목록이 바뀔 때마다 addEventListener를
 * 다시 걸면, 키를 누른 순간과 리바인드가 겹쳤을 때 이벤트를 흘린다.
 */
export function useShortcuts({ onTrigger, enabled }) {
  const [shortcuts, setShortcuts] = useState([]);
  const [loadError, setLoadError] = useState(false);

  const shortcutsRef = useRef(shortcuts);
  const enabledRef = useRef(enabled);
  const onTriggerRef = useRef(onTrigger);

  shortcutsRef.current = shortcuts;
  enabledRef.current = enabled;
  onTriggerRef.current = onTrigger;

  const reload = useCallback(async () => {
    try {
      const list = await fetchShortcuts();
      setShortcuts(Array.isArray(list) ? list : []);
      setLoadError(false);
    } catch {
      setShortcuts([]);
      setLoadError(true);
    }
  }, []);

  useEffect(() => { reload(); }, [reload]);

  useEffect(() => {
    const onKeyDown = (event) => {
      if (!enabledRef.current) return;
      // 한글 조합 중의 keydown은 조합 확정용이다 — 여기서 발사하면 입력 도중 질문이 나간다
      if (event.isComposing || event.keyCode === 229) return;

      const combo = comboFromEvent(event);
      if (!combo) return;

      const hit = shortcutsRef.current.find((s) => s.keyCombo === combo);
      if (!hit) return; // 등록 안 된 조합은 브라우저에 그대로 넘긴다

      event.preventDefault();
      onTriggerRef.current?.(hit.prompt);
    };

    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, []);

  return { shortcuts, loadError, reload };
}
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `cd check-kopilot/frontend && npm test -- useShortcuts`
Expected: PASS (6 tests)

- [ ] **Step 6: 커밋**

```bash
git add check-kopilot/frontend/src/lib/shortcutsApi.js check-kopilot/frontend/src/hooks
git commit -m "feat(frontend): 단축키 API 클라이언트와 전역 키 훅 추가"
```

---

### Task 7: 폼 입력 컴포넌트 (`StockPicker` · `KeyComboInput`)

**Files:**
- Create: `frontend/src/components/shortcuts/StockPicker.jsx`
- Create: `frontend/src/components/shortcuts/KeyComboInput.jsx`
- Test: `frontend/src/components/shortcuts/__tests__/StockPicker.test.jsx`, `frontend/src/components/shortcuts/__tests__/KeyComboInput.test.jsx`

**Interfaces:**
- Consumes: `searchStocks(q, signal)` (Task 6), `comboFromEvent`/`formatCombo`/`isMacPlatform` (Task 5), `stockLabel` (Task 5)
- Produces:
  - `<StockPicker value={string[]} onChange={(labels) => void} max={number} />` — value는 `"삼성전자(005930)"` 라벨 배열
  - `<KeyComboInput value={string|null} onChange={(combo) => void} conflictLabel={string|null} />`

- [ ] **Step 1: 실패하는 테스트 작성 (StockPicker)**

`frontend/src/components/shortcuts/__tests__/StockPicker.test.jsx`

```jsx
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach } from "vitest";
import StockPicker from "../StockPicker";

beforeEach(() => {
  vi.stubGlobal("fetch", vi.fn(() =>
    Promise.resolve({
      ok: true,
      status: 200,
      json: () => Promise.resolve([
        { code: "005930", name: "삼성전자", market: "KOSPI", type: "STOCK" },
        { code: "006400", name: "삼성SDI", market: "KOSPI", type: "STOCK" },
      ]),
    })
  ));
});

describe("StockPicker", () => {
  it("두 글자 이상 입력하면 후보를 띄우고, 고르면 칩으로 담는다", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(<StockPicker value={[]} onChange={onChange} max={2} />);

    await user.type(screen.getByRole("combobox", { name: "종목 검색" }), "삼성");

    await waitFor(() => expect(screen.getByText("삼성전자")).toBeInTheDocument());
    await user.click(screen.getByText("삼성전자"));

    expect(onChange).toHaveBeenCalledWith(["삼성전자(005930)"]);
  });

  it("담긴 칩을 지울 수 있다", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(<StockPicker value={["삼성전자(005930)"]} onChange={onChange} max={2} />);

    await user.click(screen.getByRole("button", { name: "삼성전자(005930) 제거" }));

    expect(onChange).toHaveBeenCalledWith([]);
  });

  it("최대 개수를 채우면 입력을 막는다", () => {
    render(<StockPicker value={["삼성전자(005930)"]} onChange={vi.fn()} max={1} />);

    expect(screen.getByRole("combobox", { name: "종목 검색" })).toBeDisabled();
  });
});
```

- [ ] **Step 2: 실패하는 테스트 작성 (KeyComboInput)**

`frontend/src/components/shortcuts/__tests__/KeyComboInput.test.jsx`

```jsx
import { render, screen, fireEvent } from "@testing-library/react";
import { describe, it, expect, vi } from "vitest";
import KeyComboInput from "../KeyComboInput";

describe("KeyComboInput", () => {
  it("허용 조합을 누르면 onChange로 정규화 문자열을 준다", () => {
    const onChange = vi.fn();
    render(<KeyComboInput value={null} onChange={onChange} conflictLabel={null} />);

    fireEvent.keyDown(screen.getByRole("button", { name: /키 조합/ }), {
      code: "Digit1", ctrlKey: true, shiftKey: true,
    });

    expect(onChange).toHaveBeenCalledWith("ctrl+shift+1");
  });

  it("허용되지 않는 조합은 사유를 보여주고 값을 바꾸지 않는다", () => {
    const onChange = vi.fn();
    render(<KeyComboInput value={null} onChange={onChange} conflictLabel={null} />);

    fireEvent.keyDown(screen.getByRole("button", { name: /키 조합/ }), {
      code: "KeyT", ctrlKey: true,
    });

    expect(onChange).not.toHaveBeenCalled();
    expect(screen.getByText(/Shift/)).toBeInTheDocument();
  });

  it("이미 쓰는 조합이면 충돌 상대를 알려준다", () => {
    render(
      <KeyComboInput value="ctrl+shift+1" onChange={vi.fn()} conflictLabel="삼성전자 변동성" />
    );

    expect(screen.getByText(/삼성전자 변동성/)).toBeInTheDocument();
  });
});
```

- [ ] **Step 3: 테스트가 실패하는지 확인**

Run: `cd check-kopilot/frontend && npm test -- StockPicker KeyComboInput`
Expected: FAIL — 컴포넌트 없음. `userEvent`가 없다는 에러가 나면 다음 스텝에서 설치한다.

- [ ] **Step 4: `@testing-library/user-event` 설치**

```bash
cd check-kopilot/frontend && npm install -D @testing-library/user-event
```

- [ ] **Step 5: `StockPicker.jsx` 작성**

`frontend/src/components/shortcuts/StockPicker.jsx`

```jsx
import { useEffect, useState } from "react";
import { X } from "lucide-react";
import { searchStocks } from "../../lib/shortcutsApi";
import { stockLabel } from "../../lib/promptTemplate";

const MIN_QUERY = 2;
const DEBOUNCE_MS = 200;

/**
 * 종목 자동완성 칩 입력.
 *
 * 마스터가 4천 행이라 목록을 통째로 못 내린다. 입력할 때마다 서버에 묻되,
 * 앞선 요청은 중단한다 — 늦게 도착한 옛 응답이 최신 후보를 덮으면 안 된다.
 */
export default function StockPicker({ value, onChange, max }) {
  const [query, setQuery] = useState("");
  const [results, setResults] = useState([]);
  const full = value.length >= max;

  useEffect(() => {
    if (full || query.trim().length < MIN_QUERY) {
      setResults([]);
      return;
    }
    const controller = new AbortController();
    const timer = setTimeout(() => {
      searchStocks(query.trim(), controller.signal)
        .then((found) => setResults(found ?? []))
        .catch(() => setResults([])); // 검색 실패는 자동완성만 비운다 — 입력은 계속된다
    }, DEBOUNCE_MS);

    return () => {
      clearTimeout(timer);
      controller.abort();
    };
  }, [query, full]);

  const add = (stock) => {
    const label = stockLabel(stock);
    if (value.includes(label)) return;
    onChange([...value, label]);
    setQuery("");
    setResults([]);
  };

  return (
    <div className="rounded-lg border border-slate-300 p-2 focus-within:border-accent-400">
      <div className="flex flex-wrap gap-1.5">
        {value.map((label) => (
          <span
            key={label}
            className="inline-flex items-center gap-1 rounded-full bg-slate-100 py-1 pl-2.5 pr-1 text-sm text-slate-700"
          >
            {label}
            <button
              type="button"
              aria-label={`${label} 제거`}
              onClick={() => onChange(value.filter((v) => v !== label))}
              className="flex h-5 w-5 items-center justify-center rounded-full text-slate-400 hover:bg-slate-200 hover:text-slate-600"
            >
              <X size={12} />
            </button>
          </span>
        ))}
      </div>

      <div className="relative">
        <input
          role="combobox"
          aria-label="종목 검색"
          aria-expanded={results.length > 0}
          aria-controls="stock-picker-results"
          value={query}
          disabled={full}
          onChange={(e) => setQuery(e.target.value)}
          placeholder={full ? `종목 ${max}개를 모두 채웠어요` : "종목명을 입력하세요"}
          className="mt-1 w-full bg-transparent px-1 py-1 text-base text-slate-800 placeholder:text-slate-400 focus:outline-none disabled:cursor-not-allowed"
        />

        {results.length > 0 && (
          <ul
            id="stock-picker-results"
            className="absolute z-10 mt-1 max-h-56 w-full overflow-y-auto rounded-lg border border-slate-200 bg-white py-1 shadow-lg"
          >
            {results.map((stock) => (
              <li key={stock.code}>
                <button
                  type="button"
                  onClick={() => add(stock)}
                  className="flex w-full items-center justify-between px-3 py-2 text-left text-base hover:bg-slate-50"
                >
                  <span className="text-slate-800">{stock.name}</span>
                  <span className="text-sm text-slate-400">{stock.code}</span>
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}
```

- [ ] **Step 6: `KeyComboInput.jsx` 작성**

`frontend/src/components/shortcuts/KeyComboInput.jsx`

```jsx
import { useState } from "react";
import { comboFromEvent, formatCombo, isMacPlatform } from "../../lib/keyCombo";

const RULE = "Ctrl(⌘)+Shift와 숫자·영문 한 글자 조합만 등록할 수 있어요";

/**
 * 키 조합 캡처.
 *
 * 텍스트로 받아 적게 하면 사용자가 실제로 누를 수 있는 조합인지 알 수 없다. 그래서
 * 실제 keydown을 잡되, 브라우저 예약 조합을 피하려고 범위를 좁혀 둔다.
 */
export default function KeyComboInput({ value, onChange, conflictLabel }) {
  const [hint, setHint] = useState(null);
  const mac = isMacPlatform();

  const capture = (event) => {
    // 캡처 중에는 모든 키를 먹는다 — Ctrl+S 같은 조합이 브라우저로 새어 나가면 안 된다
    event.preventDefault();
    if (["Control", "Shift", "Alt", "Meta"].includes(event.key)) return;

    const combo = comboFromEvent(event);
    if (!combo) {
      setHint(RULE);
      return;
    }
    setHint(null);
    onChange(combo);
  };

  return (
    <div>
      <button
        type="button"
        aria-label="키 조합"
        onKeyDown={capture}
        className="w-full rounded-lg border border-slate-300 px-3 py-2 text-left text-base text-slate-800 focus:border-accent-400 focus:outline-none"
      >
        {value ? formatCombo(value, mac) : "여기를 누른 뒤 키를 눌러 주세요"}
      </button>

      {conflictLabel && (
        <p className="mt-1 text-sm text-red-600">
          이미 &lsquo;{conflictLabel}&rsquo;이(가) 쓰는 조합이에요
        </p>
      )}
      {hint && !conflictLabel && <p className="mt-1 text-sm text-slate-500">{hint}</p>}
    </div>
  );
}
```

- [ ] **Step 7: 테스트 통과 확인**

Run: `cd check-kopilot/frontend && npm test -- StockPicker KeyComboInput`
Expected: PASS (6 tests)

- [ ] **Step 8: 커밋**

```bash
git add check-kopilot/frontend/src/components/shortcuts check-kopilot/frontend/package.json \
        check-kopilot/frontend/package-lock.json
git commit -m "feat(frontend): 단축키 폼 종목 선택·키 캡처 입력 추가"
```

---

### Task 8: 저장 폼 (`ShortcutFormModal`)

**Files:**
- Create: `frontend/src/components/shortcuts/ShortcutFormModal.jsx`
- Test: `frontend/src/components/shortcuts/__tests__/ShortcutFormModal.test.jsx`

**Interfaces:**
- Consumes: `StockPicker`/`KeyComboInput` (Task 7), `fetchCatalog`/`createShortcut`/`updateShortcut`/`ShortcutApiError` (Task 6), `buildPrompt`/`needsPeriod`/`PERIODS` (Task 5)
- Produces: `<ShortcutFormModal editing={ShortcutView|null} existing={ShortcutView[]} onSaved={(saved) => void} onClose={() => void} />`

- [ ] **Step 1: 실패하는 테스트 작성**

`frontend/src/components/shortcuts/__tests__/ShortcutFormModal.test.jsx`

```jsx
import { render, screen, waitFor, fireEvent } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach } from "vitest";
import ShortcutFormModal from "../ShortcutFormModal";

const CATALOG = [
  {
    toolName: "return_gap",
    label: "수익률 갭 비교",
    description: "두 대상의 기간수익률 차이",
    promptTemplate: "{targets}의 {period} 수익률 갭을 비교해줘",
    minTargets: 2,
    maxTargets: 2,
  },
  {
    toolName: "nav_disparity",
    label: "ETF 괴리율",
    description: "ETF 괴리율",
    promptTemplate: "{targets}의 괴리율을 알려줘",
    minTargets: 1,
    maxTargets: 1,
  },
];

function mockFetch({ saveStatus = 201, saveBody = {} } = {}) {
  return vi.fn((url, options = {}) => {
    const href = String(url);
    if (href.startsWith("/api/catalog")) {
      return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve(CATALOG) });
    }
    if (href.startsWith("/api/stocks")) {
      return Promise.resolve({
        ok: true,
        status: 200,
        json: () => Promise.resolve([
          { code: "005930", name: "삼성전자", market: "KOSPI", type: "STOCK" },
          { code: "000660", name: "SK하이닉스", market: "KOSPI", type: "STOCK" },
        ]),
      });
    }
    if (href.startsWith("/api/shortcuts") && options.method === "POST") {
      return Promise.resolve({
        ok: saveStatus < 400,
        status: saveStatus,
        json: () => Promise.resolve(saveBody),
      });
    }
    return Promise.reject(new Error(`unexpected fetch: ${href}`));
  });
}

async function pickStock(user, name) {
  await user.type(screen.getByRole("combobox", { name: "종목 검색" }), "삼성");
  await waitFor(() => expect(screen.getByText(name)).toBeInTheDocument());
  await user.click(screen.getByText(name));
}

beforeEach(() => {
  localStorage.clear();
  vi.stubGlobal("fetch", mockFetch());
});

describe("ShortcutFormModal", () => {
  it("종목·기간을 고르면 프롬프트가 자동으로 만들어진다", async () => {
    const user = userEvent.setup();
    render(<ShortcutFormModal editing={null} existing={[]} onSaved={vi.fn()} onClose={vi.fn()} />);
    await waitFor(() => expect(screen.getByLabelText("분석할 카탈로그")).toBeInTheDocument());

    await pickStock(user, "삼성전자");
    await pickStock(user, "SK하이닉스");

    expect(screen.getByLabelText("프롬프트")).toHaveValue(
      "삼성전자와 SK하이닉스의 최근 3개월 수익률 갭을 비교해줘"
    );
  });

  it("{period}가 없는 지표는 기간 셀렉트를 감춘다", async () => {
    const user = userEvent.setup();
    render(<ShortcutFormModal editing={null} existing={[]} onSaved={vi.fn()} onClose={vi.fn()} />);
    await waitFor(() => expect(screen.getByLabelText("분석할 카탈로그")).toBeInTheDocument());

    await user.selectOptions(screen.getByLabelText("분석할 카탈로그"), "nav_disparity");

    expect(screen.queryByLabelText("기간")).not.toBeInTheDocument();
  });

  it("사용자가 프롬프트를 고치면 이후 선택이 덮어쓰지 않는다", async () => {
    const user = userEvent.setup();
    render(<ShortcutFormModal editing={null} existing={[]} onSaved={vi.fn()} onClose={vi.fn()} />);
    await waitFor(() => expect(screen.getByLabelText("분석할 카탈로그")).toBeInTheDocument());
    await pickStock(user, "삼성전자");

    const prompt = screen.getByLabelText("프롬프트");
    await user.clear(prompt);
    await user.type(prompt, "내가 직접 쓴 질문");
    await pickStock(user, "SK하이닉스");

    expect(prompt).toHaveValue("내가 직접 쓴 질문");
  });

  it("저장하면 서버에 보내고 onSaved를 부른다", async () => {
    const saved = {
      id: "s1", keyCombo: "ctrl+shift+1", toolName: "return_gap",
      targets: ["삼성전자(005930)", "SK하이닉스(000660)"], period: "3M",
      prompt: "삼성전자와 SK하이닉스의 최근 3개월 수익률 갭을 비교해줘",
    };
    vi.stubGlobal("fetch", mockFetch({ saveStatus: 201, saveBody: saved }));
    const user = userEvent.setup();
    const onSaved = vi.fn();
    render(<ShortcutFormModal editing={null} existing={[]} onSaved={onSaved} onClose={vi.fn()} />);
    await waitFor(() => expect(screen.getByLabelText("분석할 카탈로그")).toBeInTheDocument());

    await pickStock(user, "삼성전자");
    await pickStock(user, "SK하이닉스");
    fireEvent.keyDown(screen.getByRole("button", { name: /키 조합/ }), {
      code: "Digit1", ctrlKey: true, shiftKey: true,
    });
    await user.click(screen.getByRole("button", { name: "저장" }));

    await waitFor(() => expect(onSaved).toHaveBeenCalledWith(saved));
  });

  it("종목 수가 모자라면 저장 버튼이 잠긴다", async () => {
    const user = userEvent.setup();
    render(<ShortcutFormModal editing={null} existing={[]} onSaved={vi.fn()} onClose={vi.fn()} />);
    await waitFor(() => expect(screen.getByLabelText("분석할 카탈로그")).toBeInTheDocument());

    await pickStock(user, "삼성전자"); // return_gap은 2개 필요
    fireEvent.keyDown(screen.getByRole("button", { name: /키 조합/ }), {
      code: "Digit1", ctrlKey: true, shiftKey: true,
    });

    expect(screen.getByRole("button", { name: "저장" })).toBeDisabled();
  });

  it("서버가 409를 주면 충돌 메시지를 띄운다", async () => {
    vi.stubGlobal("fetch", mockFetch({
      saveStatus: 409,
      saveBody: { code: "KEY_TAKEN", message: "이미 사용 중인 키 조합입니다: ctrl+shift+1" },
    }));
    const user = userEvent.setup();
    render(<ShortcutFormModal editing={null} existing={[]} onSaved={vi.fn()} onClose={vi.fn()} />);
    await waitFor(() => expect(screen.getByLabelText("분석할 카탈로그")).toBeInTheDocument());

    await pickStock(user, "삼성전자");
    await pickStock(user, "SK하이닉스");
    fireEvent.keyDown(screen.getByRole("button", { name: /키 조합/ }), {
      code: "Digit1", ctrlKey: true, shiftKey: true,
    });
    await user.click(screen.getByRole("button", { name: "저장" }));

    await waitFor(() =>
      expect(screen.getByText(/이미 사용 중인 키 조합/)).toBeInTheDocument()
    );
  });
});
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

Run: `cd check-kopilot/frontend && npm test -- ShortcutFormModal`
Expected: FAIL — 컴포넌트 없음

- [ ] **Step 3: 폼 작성**

`frontend/src/components/shortcuts/ShortcutFormModal.jsx`

```jsx
import { useEffect, useMemo, useState } from "react";
import { X } from "lucide-react";
import Button from "../common/Button";
import StockPicker from "./StockPicker";
import KeyComboInput from "./KeyComboInput";
import { PERIODS, buildPrompt, needsPeriod } from "../../lib/promptTemplate";
import { createShortcut, fetchCatalog, updateShortcut } from "../../lib/shortcutsApi";

const DEFAULT_PERIOD = "3M";

export default function ShortcutFormModal({ editing, existing, onSaved, onClose }) {
  const [catalog, setCatalog] = useState([]);
  const [toolName, setToolName] = useState(editing?.toolName ?? "");
  const [targets, setTargets] = useState(editing?.targets ?? []);
  const [period, setPeriod] = useState(editing?.period ?? DEFAULT_PERIOD);
  const [keyCombo, setKeyCombo] = useState(editing?.keyCombo ?? null);
  const [prompt, setPrompt] = useState(editing?.prompt ?? "");
  // 편집 중인 프리셋의 문구는 이미 사람이 확정한 것이다 — 열자마자 덮어쓰지 않는다
  const [promptEdited, setPromptEdited] = useState(Boolean(editing));
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState(null);

  useEffect(() => {
    fetchCatalog()
      .then((items) => {
        setCatalog(items ?? []);
        setToolName((current) => current || items?.[0]?.toolName || "");
      })
      .catch(() => setError("지표 목록을 불러오지 못했습니다"));
  }, []);

  const selected = useMemo(
    () => catalog.find((item) => item.toolName === toolName) ?? null,
    [catalog, toolName]
  );

  const periodUsed = selected ? needsPeriod(selected.promptTemplate) : false;

  // 선택이 바뀌면 문구를 다시 만든다. 단, 사람이 손댄 뒤로는 건드리지 않는다.
  useEffect(() => {
    if (!selected || promptEdited) return;
    setPrompt(buildPrompt({
      template: selected.promptTemplate,
      targetLabels: targets,
      periodCode: periodUsed ? period : null,
    }));
  }, [selected, targets, period, periodUsed, promptEdited]);

  const conflict = existing.find((s) => s.keyCombo === keyCombo && s.id !== editing?.id) ?? null;
  const targetsOk = selected
    && targets.length >= selected.minTargets
    && targets.length <= selected.maxTargets;
  const canSave = Boolean(selected) && targetsOk && Boolean(keyCombo) && !conflict
    && prompt.trim().length > 0 && !saving;

  const save = async () => {
    setSaving(true);
    setError(null);
    const body = {
      keyCombo,
      toolName,
      targets,
      period: periodUsed ? period : null,
      prompt: prompt.trim(),
    };
    try {
      const saved = editing
        ? await updateShortcut(editing.id, body)
        : await createShortcut(body);
      onSaved(saved);
    } catch (e) {
      setError(e.message ?? "저장에 실패했습니다");
    } finally {
      setSaving(false);
    }
  };

  const regenerate = () => {
    if (!selected) return;
    setPromptEdited(false);
    setPrompt(buildPrompt({
      template: selected.promptTemplate,
      targetLabels: targets,
      periodCode: periodUsed ? period : null,
    }));
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 p-4">
      <div className="max-h-full w-full max-w-lg overflow-y-auto rounded-2xl bg-white p-5 shadow-xl">
        <div className="mb-4 flex items-center justify-between">
          <h2 className="text-lg font-semibold text-slate-900">
            {editing ? "단축키 수정" : "단축키 추가"}
          </h2>
          <button
            type="button"
            aria-label="닫기"
            onClick={onClose}
            className="flex h-8 w-8 items-center justify-center rounded-lg text-slate-400 hover:bg-slate-100"
          >
            <X size={18} />
          </button>
        </div>

        <div className="space-y-4">
          <div>
            <label htmlFor="shortcut-tool" className="mb-1 block text-sm font-medium text-slate-700">
              분석할 카탈로그
            </label>
            <select
              id="shortcut-tool"
              aria-label="분석할 카탈로그"
              value={toolName}
              onChange={(e) => setToolName(e.target.value)}
              className="w-full rounded-lg border border-slate-300 px-3 py-2 text-base text-slate-800 focus:border-accent-400 focus:outline-none"
            >
              {catalog.map((item) => (
                <option key={item.toolName} value={item.toolName}>{item.label}</option>
              ))}
            </select>
            {selected && (
              <p className="mt-1 text-sm text-slate-500">
                종목 {selected.minTargets === selected.maxTargets
                  ? `${selected.minTargets}개`
                  : `${selected.minTargets}~${selected.maxTargets}개`}
              </p>
            )}
          </div>

          <div>
            <span className="mb-1 block text-sm font-medium text-slate-700">종목</span>
            <StockPicker
              value={targets}
              onChange={setTargets}
              max={selected?.maxTargets ?? 1}
            />
          </div>

          {periodUsed && (
            <div>
              <label htmlFor="shortcut-period" className="mb-1 block text-sm font-medium text-slate-700">
                기간
              </label>
              <select
                id="shortcut-period"
                aria-label="기간"
                value={period}
                onChange={(e) => setPeriod(e.target.value)}
                className="w-full rounded-lg border border-slate-300 px-3 py-2 text-base text-slate-800 focus:border-accent-400 focus:outline-none"
              >
                {PERIODS.map((p) => (
                  <option key={p.code} value={p.code}>{p.label}</option>
                ))}
              </select>
            </div>
          )}

          <div>
            <span className="mb-1 block text-sm font-medium text-slate-700">단축키 조합</span>
            <KeyComboInput
              value={keyCombo}
              onChange={setKeyCombo}
              conflictLabel={conflict ? conflict.prompt : null}
            />
          </div>

          <div>
            <div className="mb-1 flex items-center justify-between">
              <label htmlFor="shortcut-prompt" className="text-sm font-medium text-slate-700">
                프롬프트 예시
              </label>
              <button
                type="button"
                onClick={regenerate}
                className="text-sm text-slate-500 hover:text-accent-600"
              >
                다시 생성
              </button>
            </div>
            <textarea
              id="shortcut-prompt"
              aria-label="프롬프트"
              rows={3}
              value={prompt}
              onChange={(e) => { setPromptEdited(true); setPrompt(e.target.value); }}
              maxLength={300}
              className="w-full resize-none rounded-lg border border-slate-300 px-3 py-2 text-base text-slate-800 focus:border-accent-400 focus:outline-none"
            />
          </div>

          {error && <p className="text-sm text-red-600">{error}</p>}
        </div>

        <div className="mt-5 flex justify-end gap-2">
          <Button variant="secondary" onClick={onClose}>취소</Button>
          <Button variant="primary" onClick={save} disabled={!canSave}>저장</Button>
        </div>
      </div>
    </div>
  );
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd check-kopilot/frontend && npm test -- ShortcutFormModal`
Expected: PASS (6 tests)

- [ ] **Step 5: 커밋**

```bash
git add check-kopilot/frontend/src/components/shortcuts
git commit -m "feat(frontend): 단축키 저장 폼 추가"
```

---

### Task 9: 헤더 메뉴와 채팅 배선

**Files:**
- Create: `frontend/src/components/shortcuts/ShortcutMenu.jsx`
- Modify: `frontend/src/components/layout/GlobalHeader.jsx`
- Modify: `frontend/src/components/layout/AppLayout.jsx`
- Modify: `frontend/src/pages/ChatPage.jsx`
- Test: `frontend/src/components/shortcuts/__tests__/ShortcutMenu.test.jsx`

**Interfaces:**
- Consumes: `ShortcutFormModal` (Task 8), `useShortcuts` (Task 6), `formatCombo`/`isMacPlatform` (Task 5), `deleteShortcut` (Task 6)
- Produces:
  - `<ShortcutMenu shortcuts={ShortcutView[]} loadError={boolean} onReload={() => void} onFormOpenChange={(open) => void} />`
  - `GlobalHeader`에 `actions` prop, `AppLayout`에 `headerActions` prop

- [ ] **Step 1: 실패하는 테스트 작성**

`frontend/src/components/shortcuts/__tests__/ShortcutMenu.test.jsx`

```jsx
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach } from "vitest";
import ShortcutMenu from "../ShortcutMenu";

const SHORTCUT = {
  id: "s1",
  keyCombo: "ctrl+shift+1",
  toolName: "return_gap",
  targets: ["삼성전자(005930)", "SK하이닉스(000660)"],
  period: "3M",
  prompt: "삼성전자와 SK하이닉스의 최근 3개월 수익률 갭을 비교해줘",
};

beforeEach(() => {
  localStorage.clear();
  vi.stubGlobal("fetch", vi.fn(() =>
    Promise.resolve({ ok: true, status: 204, json: () => Promise.resolve(null) })
  ));
});

describe("ShortcutMenu", () => {
  it("저장된 단축키를 키 표기와 함께 보여준다", async () => {
    const user = userEvent.setup();
    render(
      <ShortcutMenu shortcuts={[SHORTCUT]} loadError={false} onReload={vi.fn()}
                    onFormOpenChange={vi.fn()} />
    );

    await user.click(screen.getByRole("button", { name: "단축키" }));

    expect(screen.getByText(/Ctrl \+ Shift \+ 1|⌘ \+ ⇧ \+ 1/)).toBeInTheDocument();
    expect(screen.getByText(SHORTCUT.prompt)).toBeInTheDocument();
  });

  it("삭제하면 서버에 지우고 목록을 다시 부른다", async () => {
    const user = userEvent.setup();
    const onReload = vi.fn();
    render(
      <ShortcutMenu shortcuts={[SHORTCUT]} loadError={false} onReload={onReload}
                    onFormOpenChange={vi.fn()} />
    );

    await user.click(screen.getByRole("button", { name: "단축키" }));
    await user.click(screen.getByRole("button", { name: /삭제/ }));

    await waitFor(() => expect(onReload).toHaveBeenCalled());
    expect(fetch).toHaveBeenCalledWith("/api/shortcuts/s1", expect.objectContaining({
      method: "DELETE",
    }));
  });

  it("폼을 열면 상위에 알린다 — 키 캡처 중 단축키가 발사되면 안 된다", async () => {
    const user = userEvent.setup();
    const onFormOpenChange = vi.fn();
    render(
      <ShortcutMenu shortcuts={[]} loadError={false} onReload={vi.fn()}
                    onFormOpenChange={onFormOpenChange} />
    );

    await user.click(screen.getByRole("button", { name: "단축키" }));
    await user.click(screen.getByRole("button", { name: "단축키 추가" }));

    expect(onFormOpenChange).toHaveBeenCalledWith(true);
  });

  it("목록을 못 불러오면 다시 시도를 보여준다", async () => {
    const user = userEvent.setup();
    const onReload = vi.fn();
    render(
      <ShortcutMenu shortcuts={[]} loadError onReload={onReload} onFormOpenChange={vi.fn()} />
    );

    await user.click(screen.getByRole("button", { name: "단축키" }));
    await user.click(screen.getByRole("button", { name: "다시 시도" }));

    expect(onReload).toHaveBeenCalled();
  });
});
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

Run: `cd check-kopilot/frontend && npm test -- ShortcutMenu`
Expected: FAIL — 컴포넌트 없음

- [ ] **Step 3: `ShortcutMenu.jsx` 작성**

`frontend/src/components/shortcuts/ShortcutMenu.jsx`

```jsx
import { useState } from "react";
import { Keyboard, Plus, Trash2, Pencil } from "lucide-react";
import ShortcutFormModal from "./ShortcutFormModal";
import { formatCombo, isMacPlatform } from "../../lib/keyCombo";
import { deleteShortcut } from "../../lib/shortcutsApi";

export default function ShortcutMenu({ shortcuts, loadError, onReload, onFormOpenChange }) {
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState(null);
  const [formOpen, setFormOpen] = useState(false);
  const mac = isMacPlatform();

  const openForm = (shortcut) => {
    setEditing(shortcut);
    setFormOpen(true);
    setOpen(false);
    onFormOpenChange(true);
  };

  const closeForm = () => {
    setFormOpen(false);
    setEditing(null);
    onFormOpenChange(false);
  };

  const remove = async (id) => {
    try {
      await deleteShortcut(id);
    } catch {
      // 삭제 실패도 목록을 다시 부른다 — 서버 상태가 화면의 진실이다
    }
    onReload();
  };

  return (
    <div className="relative">
      <button
        type="button"
        aria-label="단축키"
        onClick={() => setOpen((v) => !v)}
        className="flex h-9 items-center gap-1.5 rounded-lg px-2 text-slate-600 hover:bg-slate-100"
      >
        <Keyboard size={18} />
        <span className="hidden text-base sm:block">단축키</span>
      </button>

      {open && (
        <div className="absolute right-0 z-40 mt-1 w-80 rounded-xl border border-slate-200 bg-white p-2 shadow-lg">
          {loadError ? (
            <div className="px-2 py-3 text-sm text-slate-500">
              불러오지 못했습니다
              <button
                type="button"
                onClick={onReload}
                className="ml-2 text-accent-600 hover:underline"
              >
                다시 시도
              </button>
            </div>
          ) : (
            <ul className="max-h-72 overflow-y-auto">
              {shortcuts.length === 0 && (
                <li className="px-2 py-3 text-sm text-slate-500">아직 만든 단축키가 없어요</li>
              )}
              {shortcuts.map((s) => (
                <li key={s.id} className="flex items-start gap-2 rounded-lg px-2 py-2 hover:bg-slate-50">
                  <div className="min-w-0 flex-1">
                    <p className="text-sm font-medium text-slate-900">{formatCombo(s.keyCombo, mac)}</p>
                    <p className="truncate text-sm text-slate-500">{s.prompt}</p>
                  </div>
                  <button
                    type="button"
                    aria-label={`${s.keyCombo} 수정`}
                    onClick={() => openForm(s)}
                    className="flex h-7 w-7 items-center justify-center rounded-lg text-slate-400 hover:bg-slate-200 hover:text-slate-600"
                  >
                    <Pencil size={14} />
                  </button>
                  <button
                    type="button"
                    aria-label={`${s.keyCombo} 삭제`}
                    onClick={() => remove(s.id)}
                    className="flex h-7 w-7 items-center justify-center rounded-lg text-slate-400 hover:bg-red-50 hover:text-red-600"
                  >
                    <Trash2 size={14} />
                  </button>
                </li>
              ))}
            </ul>
          )}

          <button
            type="button"
            onClick={() => openForm(null)}
            className="mt-1 flex w-full items-center gap-1.5 rounded-lg px-2 py-2 text-sm font-medium text-accent-600 hover:bg-accent-50"
          >
            <Plus size={16} /> 단축키 추가
          </button>
        </div>
      )}

      {formOpen && (
        <ShortcutFormModal
          editing={editing}
          existing={shortcuts}
          onSaved={() => { closeForm(); onReload(); }}
          onClose={closeForm}
        />
      )}
    </div>
  );
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd check-kopilot/frontend && npm test -- ShortcutMenu`
Expected: PASS (4 tests)

- [ ] **Step 5: 헤더에 슬롯 열기**

`frontend/src/components/layout/GlobalHeader.jsx` — 시그니처와 프로필 버튼 앞줄만 고친다.

```jsx
export default function GlobalHeader({ title = "새 대화", onMenuClick, actions }) {
```

그리고 우측 프로필 버튼(현재 파일의 29~37행)을 아래 묶음으로 통째로 교체한다. 프로필 버튼 내용은 그대로이고 바깥에 `div`와 `{actions}`만 생겼다.

```jsx
      <div className="flex shrink-0 items-center gap-1">
        {actions}
        <button className="flex shrink-0 items-center gap-2 rounded-full py-1 pl-1 pr-2 hover:bg-slate-100 lg:pr-3">
          <div className="flex h-7 w-7 items-center justify-center rounded-full bg-slate-200 text-base font-medium text-slate-600 lg:h-8 lg:w-8 lg:text-lg">
            김
          </div>
          <span className="hidden text-lg text-slate-600 sm:block lg:text-[19px]">
            김트레이더
          </span>
          <ChevronDown size={14} className="hidden text-slate-400 sm:block" />
        </button>
      </div>
```

`frontend/src/components/layout/AppLayout.jsx` — props에 `headerActions`를 더하고 헤더로 넘긴다.

```jsx
export default function AppLayout({
  headerTitle,
  headerActions,
  children,
  // …기존 props 그대로
}) {
```

```jsx
        <GlobalHeader
          title={headerTitle}
          actions={headerActions}
          onMenuClick={() => setSidebarOpen(true)}
        />
```

- [ ] **Step 6: `ChatPage`에 배선**

`frontend/src/pages/ChatPage.jsx`

import 3줄을 추가한다.

```jsx
import ShortcutMenu from "../components/shortcuts/ShortcutMenu";
import { useShortcuts } from "../hooks/useShortcuts";
```

상태 하나를 추가한다(`const [tourOpen, setTourOpen] = useState(false);` 아래).

```jsx
  // 폼이 열려 있는 동안 키 트리거를 끈다 — 키 캡처 중에 그 단축키가 발사되면 안 된다
  const [shortcutFormOpen, setShortcutFormOpen] = useState(false);
```

`ask` 정의 바로 아래(`handleSelectCandidate` 앞)에 훅을 건다. **`if (loading) return <LoadingScreen …>` 보다 반드시 위여야 한다** — 조기 반환 뒤에 두면 렌더마다 훅 개수가 달라져 React가 터진다.

```jsx
  const { shortcuts, loadError: shortcutsLoadError, reload: reloadShortcuts } = useShortcuts({
    onTrigger: ask,
    enabled: !sending && !shortcutFormOpen,
  });
```

`AppLayout`에 prop 하나를 더한다.

```jsx
    <AppLayout
      headerTitle="새 대화"
      headerActions={
        <ShortcutMenu
          shortcuts={shortcuts}
          loadError={shortcutsLoadError}
          onReload={reloadShortcuts}
          onFormOpenChange={setShortcutFormOpen}
        />
      }
      sidebarOpen={sidebarOpen}
```

- [ ] **Step 7: 프론트 전체 테스트로 회귀 확인**

Run: `cd check-kopilot/frontend && npm test`
Expected: 전부 PASS. `ChatPage`를 렌더하는 기존 테스트가 있으면 `/api/shortcuts` fetch가 추가로 나가지만, `useShortcuts`가 실패를 삼키므로 깨지지 않아야 한다. 깨지면 그 테스트의 fetch mock에 `/api/shortcuts` 분기를 더한다.

- [ ] **Step 8: 린트**

Run: `cd check-kopilot/frontend && npm run lint`
Expected: 새 파일에 경고 없음

- [ ] **Step 9: 실제 앱에서 손으로 확인**

```bash
cd check-kopilot && docker compose up -d
cd backend && ./gradlew bootRun   # 별도 터미널
cd check-kopilot/frontend && npm run dev
```

브라우저에서: 헤더 "단축키" → "단축키 추가" → 삼성전자·SK하이닉스 선택 → Ctrl+Shift+1 캡처 → 저장 → 모달 닫힘 → **Ctrl+Shift+1** → 질문이 전송되고 카드가 뜨는지 확인. 새로고침 후에도 목록이 남는지 확인.

- [ ] **Step 10: 커밋**

```bash
git add check-kopilot/frontend/src
git commit -m "feat(frontend): 헤더 단축키 메뉴와 전역 키 트리거 연결"
```

---

### Task 10: 문서 갱신

**Files:**
- Modify: `docs/api.md`
- Modify: `CLAUDE.md`

- [ ] **Step 1: `docs/api.md`에 엔드포인트 3종 추가**

기존 문서의 "엔드포인트 개요" 표에 네 줄을 더하고, 문서 끝에 절을 추가한다. 형식은 기존 절(`## 4. 카탈로그 추가 요청`)을 따른다.

```markdown
## 7. 지표 카탈로그 조회 — REST

### Description
단축키 프리셋 폼이 지표 목록과 프롬프트 템플릿을 받아 간다. 카탈로그의 단일 출처는 백엔드 실행기다.

### Endpoint
`GET /api/catalog`

### Response
```json
[
  {
    "toolName": "return_gap",
    "label": "수익률 갭 비교",
    "description": "두 대상(종목/지수/ETF)의 기간수익률 차이(수익률 갭)를 계산한다. …",
    "promptTemplate": "{targets}의 {period} 수익률 갭을 비교해줘",
    "minTargets": 2,
    "maxTargets": 2
  }
]
```

`promptTemplate`의 치환 토큰은 `{targets}`·`{period}` 둘뿐이다. `{period}`가 없으면 그 지표는 기간을 받지 않는다.

---

## 8. 종목 자동완성 — REST

### Description
단축키 폼의 종목 검색. 되묻기와 같은 검색기(`StockResolver`)를 쓴다.

### Endpoint
`GET /api/stocks?q=삼성&limit=8`

`q`가 2자 미만이면 빈 배열을 준다. `limit`은 기본 8, 최대 20.

### Response
```json
[{ "code": "005930", "name": "삼성전자", "market": "KOSPI", "type": "STOCK" }]
```

---

## 9. 단축키 프리셋 — REST

### Description
종목·지표·기간을 묶어 키 조합에 걸어 두는 프리셋. 로그인이 없으므로 소유자는 브라우저가 발급한 `X-Device-Id`다.

### Endpoint
| 메서드 | 경로 | 응답 |
|---|---|---|
| GET | `/api/shortcuts` | 200 `ShortcutView[]` |
| POST | `/api/shortcuts` | 201 `ShortcutView` |
| PUT | `/api/shortcuts/{id}` | 200 `ShortcutView` |
| DELETE | `/api/shortcuts/{id}` | 204 |

### Request
헤더 `X-Device-Id: <UUID>` 필수.

```json
{
  "keyCombo": "ctrl+shift+1",
  "toolName": "return_gap",
  "targets": ["삼성전자(005930)", "SK하이닉스(000660)"],
  "period": "3M",
  "prompt": "삼성전자와 SK하이닉스의 최근 3개월 수익률 갭을 비교해줘"
}
```

- `keyCombo`는 `ctrl+shift+<숫자|영문>` 형식만 받는다(mac의 ⌘도 `ctrl`로 저장).
- `targets` 개수는 해당 지표의 `minTargets`~`maxTargets` 안이어야 한다.
- `period`는 `1M | 3M | 6M | 1Y` 또는 null.
- `prompt`는 1~300자이며 **실제로 전송되는 문구**다.

### Status Code
| 코드 | 의미 | 바디 `code` |
|---|---|---|
| 400 | 검증 실패 | `KEY_COMBO_INVALID` · `TOOL_UNKNOWN` · `TARGET_COUNT_INVALID` · `PROMPT_INVALID` · `DEVICE_ID_INVALID` |
| 404 | 없거나 다른 기기의 프리셋 | `NOT_FOUND` |
| 409 | 같은 기기에서 이미 쓰는 키 조합 | `KEY_TAKEN` |

에러 바디는 `{"code": "...", "message": "..."}`.
```

- [ ] **Step 2: `CLAUDE.md` 모듈 표에 `shortcut` 추가**

`demand` 행 아래에 붙인다.

```markdown
| `shortcut` | 단축키 프리셋 CRUD (소유자 = `X-Device-Id`) | 구현됨 |
```

같은 표 위쪽 `demand` 행의 상태도 실제와 맞는지 확인하고, 어긋나면 이 PR에서 건드리지 않는다(범위 밖).

- [ ] **Step 3: 전체 테스트 마지막 확인**

```bash
cd check-kopilot/backend && ./gradlew test
cd ../frontend && npm test && npm run lint
```
Expected: 모두 통과

- [ ] **Step 4: 커밋**

```bash
git add docs/api.md CLAUDE.md
git commit -m "docs: 단축키 프리셋 API 명세 추가"
```

---

## 완료 조건

- `./gradlew test`와 `npm test`가 모두 통과한다.
- 브라우저에서 단축키를 만들고, 키를 눌러 카드가 뜨고, 새로고침 후에도 목록이 남는다.
- 같은 키를 두 번 등록하면 폼이 충돌을 알린다.
- `docs/api.md`가 실제 엔드포인트와 일치한다.
