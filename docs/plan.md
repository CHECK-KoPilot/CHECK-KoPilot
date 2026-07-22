# Check Kopilot 구현 계획 (Implementation Plan)

> 설계 스펙: [spec.md](spec.md) · CHECK API 조사: [check-api/README.md](check-api/README.md) · 문서 인덱스: [README.md](README.md)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 트레이더가 자연어로 질문하면 Claude가 지표 tool을 선택하고, 백엔드 Java 코드가 CHECK API 호출·계산을 수행해 근거 패널·차트·xlsx가 달린 검증 가능한 답변 카드를 돌려주는 채팅형 웹앱(MVP)을 만든다.

**Architecture:** Spring Boot 백엔드(chat / catalog / guide / demand / checkapi / export 모듈) + React SPA + MySQL 8 + Redis 7. 모든 수치 계산은 백엔드가 수행하고 LLM은 tool 선택·파라미터 추출·해설만 담당한다. 카드 데이터는 백엔드 JSON을 프론트가 직접 렌더한다. 로그인 인증은 MVP 제외 — 브라우저가 발급한 익명 세션 ID로 대화 컨텍스트를 격리한다.

**Tech Stack:** Java 21, Spring Boot 3.5.x, Spring AI(`spring-ai-starter-model-anthropic`, model `claude-opus-4-8`), MySQL 8 + Redis 7(docker-compose), Apache POI 5.3, Vite + React 19 + TypeScript, Recharts, vitest.

> **스펙 대비 구현 결정 3가지** (스펙 8절은 "Claude API / Spring AI"를 언급):
> 1. **Spring AI를 사용하되 자동 tool 실행을 끄고 수동 tool 루프를 돌린다**(`ToolCallingChatOptions.internalToolExecutionEnabled(false)`). tool 실행 도중 SSE로 카드 이벤트를 밀어내는 커스텀 디스패치 루프가 필요한데, 자동 실행은 그 훅 지점을 주지 않는다. 기획서 "사용 기술" 표(AI: Spring AI, Claude API)와도 일치한다.
> 2. **해설 텍스트는 토큰 단위 스트리밍 대신 SSE `text` 이벤트 1건으로 전송한다.** (SSE 전송 채널 자체는 유지) 토큰 스트리밍은 tool 루프와 결합 시 복잡도가 높아 MVP 이후 폴리시 항목으로 남긴다. 데모 체감 차이는 프론트 타자 효과로 보완 가능.
> 3. **DB는 MySQL 8, 세션·캐시는 Redis 7.** 스펙 8절은 PostgreSQL을 적었으나 기획서 "사용 기술" 표(MySQL, Redis)를 따른다. 단기 캐시(Redis TTL)와 장애 폴백 스냅샷(MySQL `check_fallback`)을 분리해 스펙 10절의 "데모 종목 풀 사전 수집"이 재기동에도 살아남게 한다.

## Global Constraints

- Java toolchain **21**, Spring Boot **3.5.x**, Spring AI **`org.springframework.ai:spring-ai-starter-model-anthropic`** (BOM `org.springframework.ai:spring-ai-bom`)
- Claude 모델 ID는 정확히 **`claude-opus-4-8`**. API 키는 환경변수 `ANTHROPIC_API_KEY` → `spring.ai.anthropic.api-key`
- **tool 자동 실행 금지**: `internalToolExecutionEnabled=false`로 두고 `ToolDispatcher`가 직접 실행한다. 자동 실행이 켜지면 SSE 카드 이벤트가 사라진다
- CHECK API 키는 환경변수 `CHECK_API_KEY`, base-url 등은 `application.yml`의 `checkapi.*`
- CHECK API 엔드포인트·응답 필드는 `docs/check-api/README.md`와 `docs/check-api/specs.json` 참조 (2026-07-22 실사이트 번들에서 추출한 776건 명세)
- 로그인 인증(회원가입·비밀번호·Spring Security) 없음. 대화 컨텍스트는 익명 세션 ID로만 격리한다
- **모든 수치 계산은 백엔드 Java 코드가 수행한다.** LLM 텍스트의 수치를 카드에 쓰지 않는다
- 지표 답변에는 반드시 근거(호출 API+명세 링크, 원본 수치, 공식, 중간 계산값)를 포함한다
- 투자 판단·권유·전망 생성 금지. 화면 하단 고지 문구(변경 금지): **"본 자료는 AI가 시장 데이터 기반으로 생성한 정보성 자료이며 투자권유가 아닙니다."**
- 백엔드 패키지 루트: `com.koscom.kopilot`
- 코드 저장 위치: 리포지토리 루트 아래 `check-kopilot/backend`, `check-kopilot/frontend`
- 커밋 메시지는 conventional commits (`feat:`, `test:`, `chore:` ...)

## 파일 구조 (전체 조감)

```
check-kopilot/
  docker-compose.yml
  backend/
    build.gradle, settings.gradle
    src/main/java/com/koscom/kopilot/
      KopilotApplication.java
      checkapi/    CheckApiClient, RestCheckApiClient, CachingCheckApiClient,
                   RedisCacheStore, JdbcFallbackStore, CheckApiConfig,
                   FixtureCheckApiClient, CheckApiProperties, CheckApiException,
                   DailyQuote, NavQuote, StockInfo, StockResolver, JdbcStockResolver,
                   StockMasterLoader, AmbiguousStockException, StockNotFoundException
      domain/      MetricResult, Calculations, MetricException
      catalog/     MetricExecutor, ExecutorSupport, CatalogService,
                   ReturnGapExecutor, VolatilityExecutor, NavDisparityExecutor,
                   MaDisparityExecutor, ReturnRankingExecutor, PeriodSummaryExecutor
      guide/       FieldDictionary, ApiSpecIndex, ApiSpecEntry, GuideService
      demand/      DemandRecorder, DemandService, CatalogRequestController,
                   AdminController, AdminTokenFilter, DemandSummary
      chat/        ChatController, ChatService, ChatConfig, ToolDispatcher, DispatchResult,
                   KopilotTools, SessionIds, ConversationStore, RedisConversationStore,
                   ConversationCodec, ChatLogService, SystemPrompt
      export/      CardStore, XlsxExportService, ExportController
    src/main/resources/
      application.yml, schema.sql, stock-master.csv,
      check-api/ fcodes.json, apis.json, synonyms.yaml, api-aliases.yaml
    src/test/resources/fixtures/*.json
    src/test/resources/eval-cases.yaml
    src/test/java/... (각 태스크의 테스트)
  frontend/
    vite.config.ts, package.json
    src/ App.tsx, main.tsx, api.ts, types.ts, session.ts,
         components/ MetricCard.tsx, EvidencePanel.tsx, GuideCard.tsx,
                     ClarifyChips.tsx, MessageList.tsx
         admin/ AdminPage.tsx, adminApi.ts
    src/components/__tests__/MetricCard.test.tsx
    src/admin/__tests__/AdminPage.test.tsx
```

설계 원칙: 지표 1종 = 실행기 클래스 1개(지표 추가 = 클래스 추가). 카드 스키마는 `MetricResult` 1종으로 통일해 프론트의 지표별 작업을 제거. CHECK API 실명세 의존부는 `RestCheckApiClient` 한 클래스에 격리.

---

### Task 1: 프로젝트 스캐폴딩 (git, Gradle, MySQL·Redis, 헬스체크)

**Files:**
- Create: `check-kopilot/docker-compose.yml`
- Create: `check-kopilot/backend/settings.gradle`, `check-kopilot/backend/build.gradle`
- Create: `check-kopilot/backend/src/main/java/com/koscom/kopilot/KopilotApplication.java`
- Create: `check-kopilot/backend/src/main/resources/application.yml`
- Create: `check-kopilot/backend/src/main/resources/schema.sql`
- Create: `.gitignore` (리포 루트)

**Interfaces:**
- Consumes: 없음 (최초 태스크)
- Produces: 실행 가능한 Spring Boot 앱(`:8080`), MySQL 8(`localhost:3307/kopilot`), Redis 7(`localhost:6379`), 이후 모든 태스크가 사용할 `schema.sql` 테이블 5종(`stock_master`, `check_fallback`, `card`, `chat_log`, `catalog_request`)
  - ※ 기존 `check_cache`(단기 캐시)는 Redis로 이관되어 테이블에서 제거하고, 영속 폴백 전용 테이블 `check_fallback`으로 대체한다 (스펙 10절 "데모 종목 풀 사전 수집" 대응)

- [ ] **Step 1: git 초기화 및 .gitignore 작성**

리포지토리 루트(`/Users/jinhyeok/dev/koscom`)는 아직 git 저장소가 아니다.

```bash
cd /Users/jinhyeok/dev/koscom && git init
```

`.gitignore` (리포 루트):

```
.DS_Store
check-kopilot/backend/build/
check-kopilot/backend/.gradle/
check-kopilot/frontend/node_modules/
check-kopilot/frontend/dist/
*.iml
.idea/
.env
```

```bash
git add .gitignore docs skills-lock.json repomix-output.xml && git commit -m "chore: init repository with spec and plan docs"
```

- [ ] **Step 2: docker-compose와 Gradle 프로젝트 작성**

`check-kopilot/docker-compose.yml`:

```yaml
services:
  db:
    image: mysql:8.4
    environment:
      MYSQL_DATABASE: kopilot
      MYSQL_USER: kopilot
      MYSQL_PASSWORD: kopilot
      MYSQL_ROOT_PASSWORD: root
      TZ: Asia/Seoul
    command:
      - --character-set-server=utf8mb4
      - --collation-server=utf8mb4_0900_ai_ci
    ports:
      # 호스트 3307 → 컨테이너 3306. 로컬에 이미 MySQL이 3306을 쓰고 있어도 충돌하지 않는다
      - "3307:3306"
    volumes:
      - kopilot-db:/var/lib/mysql
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost", "-ukopilot", "-pkopilot"]
      interval: 5s
      retries: 20

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
    command: ["redis-server", "--save", "", "--appendonly", "no"]

volumes:
  kopilot-db:
```

> Redis는 세션 컨텍스트·단기 캐시 전용이므로 영속화를 끈다 — 재기동 시 유실돼도 MySQL 이력(`chat_log`)과 폴백 스냅샷(`check_fallback`)으로 서비스가 지속된다.

`check-kopilot/backend/settings.gradle`:

```groovy
rootProject.name = 'kopilot-backend'
```

`check-kopilot/backend/build.gradle`:

```groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.5.3'
    id 'io.spring.dependency-management' version '1.1.7'
}

group = 'com.koscom'
version = '0.1.0'

java {
    toolchain { languageVersion = JavaLanguageVersion.of(21) }
}

repositories { mavenCentral() }

ext {
    // 2026-07-22 스파이크에서 Spring Boot 3.5.3과 정상 해석 확인
    set('springAiVersion', '1.0.0')
}

dependencyManagement {
    imports { mavenBom "org.springframework.ai:spring-ai-bom:${springAiVersion}" }
}

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-jdbc'
    implementation 'org.springframework.boot:spring-boot-starter-data-redis'
    implementation 'org.springframework.ai:spring-ai-starter-model-anthropic'
    implementation 'com.fasterxml.jackson.datatype:jackson-datatype-jsr310'
    implementation 'org.apache.poi:poi-ooxml:5.3.0'
    runtimeOnly 'com.mysql:mysql-connector-j'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}

tasks.named('test') { useJUnitPlatform() }
```

> 제거된 의존성: `com.anthropic:anthropic-java:2.34.0`, `org.postgresql:postgresql`.
> `jackson-datatype-jsr310`은 이후 Task들이 모두 쓰므로 여기서 한 번에 넣는다.

`KopilotApplication.java`:

```java
package com.koscom.kopilot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
public class KopilotApplication {
    public static void main(String[] args) {
        SpringApplication.run(KopilotApplication.class, args);
    }

    @RestController
    static class HealthController {
        @GetMapping("/api/health")
        public String health() { return "ok"; }
    }
}
```

`application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3307/kopilot?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Seoul&characterEncoding=UTF-8
    username: kopilot
    password: kopilot
  sql:
    init:
      mode: always
  data:
    redis:
      host: localhost
      port: 6379
  ai:
    anthropic:
      api-key: ${ANTHROPIC_API_KEY:}
      chat:
        options:
          model: claude-opus-4-8
          max-tokens: 4096
          temperature: 0.2

kopilot:
  cache-ttl: PT10M           # CHECK API 단기 응답 캐시(Redis) TTL
  session-ttl: PT2H          # 익명 세션 대화 컨텍스트(Redis) TTL
  max-history-turns: 20      # Claude에 재전송할 최대 과거 턴 수 (컨텍스트 비용 상한)

admin:
  # 로그인 인증이 없으므로 Admin API는 공유 시크릿으로만 보호한다 (Task 15 참고)
  token: ${ADMIN_TOKEN:kopilot-demo}

checkapi:
  base-url: https://checkapi.koscom.co.kr
  cust-id: ${CHECK_CUST_ID:}     # CHECK 단말 고객번호 10자리
  api-key: ${CHECK_API_KEY:}     # auth_key
  # 실제 엔드포인트 (2026-07-22 실호출 확인 — docs/check-api/README.md 참조)
  # 호출 규약: POST 전용(GET 불가), body는 JSON 또는 form.
  # 공통 파라미터: cust_id, auth_key, jcode, sdate(YYYYMMDD), edate(YYYYMMDD)
  # 응답: {"success":true,"results":[...]} — HTTP status는 실패해도 항상 200이므로 success 필드로 판정.
  #       존재하지 않는 종목코드는 에러가 아니라 results:[] 로 온다.
  #       시계열은 최신→과거 내림차순. 수치는 문자열(BigDecimal(String)로 파싱).
  paths:
    stock-daily: /stock/m001/hist_info    # 거래소(KOSPI) 종목 일별정보 — 종가 F15001, OHLC F15009/10/11,
                                          #   ETP지표가치(NAV/IV) F15301 포함 → ETF 괴리율도 이 호출로 계산
    kosdaq-daily: /stock/m003/hist_info   # 코스닥 종목 일별정보
    index-daily: /stock/m002/hist_info    # 거래소 업종(지수) 일별정보 — 코스피는 jcode=1
    kosdaq-index-daily: /stock/m004/hist_info   # 코스닥 지수도 jcode=1
  # 종목 마스터 생성용 (Task 3 stock-master.csv)
  code-paths:
    kospi: /stock/m001/code_info
    kospi-etf: /stock/m001/code_etf_info
    kospi-etn: /stock/m001/code_etn_info
    kosdaq: /stock/m003/code_info
    index: /stock/m002/code_info
    kosdaq-index: /stock/m004/code_info
```

> yml의 `spring.ai.anthropic.chat.options`에 의존하지 않고 **런타임 `ToolCallingChatOptions`에서 `model`·`maxTokens`를 직접 지정한다**(Task 13). `ToolCallingChatOptions.Builder`가 `model(String)`/`maxTokens(Integer)`를 제공하는 것을 스파이크에서 확인했으므로, yml 옵션 병합 여부에 기대지 않는 편이 안전하다. yml 값은 폴백으로만 남긴다.

`schema.sql`:

```sql
CREATE TABLE IF NOT EXISTS stock_master (
    code    VARCHAR(12)  NOT NULL,
    name    VARCHAR(80)  NOT NULL,
    market  VARCHAR(10)  NOT NULL,          -- KOSPI | KOSDAQ | INDEX
    type    VARCHAR(10)  NOT NULL,          -- STOCK | ETF | INDEX
    PRIMARY KEY (code),
    KEY idx_stock_master_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- CHECK API 장애 대비 영속 폴백 스냅샷 (단기 캐시는 Redis가 담당)
CREATE TABLE IF NOT EXISTS check_fallback (
    cache_key  VARCHAR(200) NOT NULL,
    payload    LONGTEXT     NOT NULL,
    fetched_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (cache_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS card (
    id         CHAR(36)     NOT NULL,
    session_id VARCHAR(64)  NOT NULL,
    payload    LONGTEXT     NOT NULL,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_card_session (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS chat_log (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    session_id VARCHAR(64)  NOT NULL,
    role       VARCHAR(20)  NOT NULL,       -- user | assistant | tool_call | tool_result | error
    tool_name  VARCHAR(60)  NULL,
    content    LONGTEXT     NOT NULL,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_chat_log_session (session_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 수요조사 적재 (가이드 카드 발생 = AUTO, 추가 요청 버튼 = EXPLICIT)
CREATE TABLE IF NOT EXISTS catalog_request (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    session_id      VARCHAR(64)  NULL,
    topic           VARCHAR(255) NOT NULL,  -- 집계 키 (LLM이 넘긴 topic 원문, 255자 절단)
    question        LONGTEXT     NULL,      -- 사용자의 원 질문(있으면)
    matched_api_ids VARCHAR(255) NULL,      -- 가이드가 제시한 CHECK API id들 (콤마 구분)
    source          VARCHAR(16)  NOT NULL DEFAULT 'AUTO',   -- AUTO | EXPLICIT
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_catalog_request_topic (topic),
    KEY idx_catalog_request_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

**Postgres→MySQL 변환 대응표** (계획 내 다른 Task에도 동일 적용):

| Postgres | MySQL 8 |
|---|---|
| `SERIAL` / `BIGSERIAL` | `BIGINT AUTO_INCREMENT` |
| `TEXT` | `LONGTEXT` (집계·인덱스 키는 `VARCHAR(255)`) |
| `UUID` | `CHAR(36)` |
| `TIMESTAMP DEFAULT now()` | `DATETIME DEFAULT CURRENT_TIMESTAMP` |
| `ON CONFLICT DO NOTHING` | `INSERT IGNORE` |
| `ON CONFLICT DO UPDATE` | `ON DUPLICATE KEY UPDATE` |
| `ILIKE` | `LIKE` (utf8mb4_0900_ai_ci는 대소문자 무시) |
| `length()` | `CHAR_LENGTH()` |

- [ ] **Step 3: 기동 확인**

```bash
cd /Users/jinhyeok/dev/koscom/check-kopilot && docker compose up -d
docker compose ps                             # Expected: db, redis 모두 Up (db가 Created면 포트 충돌)
docker compose exec redis redis-cli ping      # Expected: PONG
cd backend && ./gradlew bootRun &   # gradle wrapper가 없으면 먼저: gradle wrapper --gradle-version 8.14
sleep 25 && curl -s localhost:8080/api/health
```

Expected: `ok` 출력. MySQL에 테이블 5개 생성 확인:

```bash
docker compose exec db mysql -ukopilot -pkopilot kopilot -e 'SHOW TABLES;'
```

Expected: `stock_master, check_fallback, card, chat_log, catalog_request` 5개 행.

- [ ] **Step 4: Commit**

```bash
git add check-kopilot && git commit -m "feat: scaffold spring boot backend with mysql, redis and health endpoint"
```
---

### Task 2: CHECK API 클라이언트 (도메인 레코드, REST 구현, 픽스처 구현)

**Files:**
- Create: `backend/src/main/java/com/koscom/kopilot/checkapi/DailyQuote.java`, `NavQuote.java`, `StockInfo.java`, `CheckApiException.java`
- Create: `backend/src/main/java/com/koscom/kopilot/checkapi/CheckApiClient.java`, `CheckApiProperties.java`, `RestCheckApiClient.java`, `FixtureCheckApiClient.java`
- Create: `backend/src/main/resources/fixtures/daily-005930.json` 외 픽스처 7개 (main 리소스 — `fixture` 프로파일이 런타임에도 읽는다)
- Test: `backend/src/test/java/com/koscom/kopilot/checkapi/FixtureCheckApiClientTest.java`

**Interfaces:**
- Consumes: Task 1의 `checkapi.*` 설정
- Produces (이후 전 태스크가 사용):
  - `record DailyQuote(LocalDate date, double open, double high, double low, double close, long volume)`
  - `record NavQuote(LocalDate date, double marketPrice, double nav)`
  - `record StockInfo(String code, String name, String market, String type)` — type ∈ `STOCK|ETF|INDEX`
  - `interface CheckApiClient { List<DailyQuote> dailyQuotes(StockInfo inst, LocalDate from, LocalDate to); List<NavQuote> etfNav(StockInfo etf, LocalDate from, LocalDate to); }`
  - `class CheckApiException extends RuntimeException` (생성자 `(String message)`, `(String message, Throwable cause)`)

- [ ] **Step 1: 도메인 레코드와 인터페이스 작성** (테스트 대상의 시그니처가 먼저 필요하므로 레코드/인터페이스는 테스트와 함께 커밋)

```java
package com.koscom.kopilot.checkapi;

import java.time.LocalDate;

public record DailyQuote(LocalDate date, double open, double high, double low,
                         double close, long volume) {}
```

```java
package com.koscom.kopilot.checkapi;

import java.time.LocalDate;

public record NavQuote(LocalDate date, double marketPrice, double nav) {}
```

```java
package com.koscom.kopilot.checkapi;

public record StockInfo(String code, String name, String market, String type) {
    public boolean isEtf()   { return "ETF".equals(type); }
    public boolean isIndex() { return "INDEX".equals(type); }
}
```

```java
package com.koscom.kopilot.checkapi;

public class CheckApiException extends RuntimeException {
    public CheckApiException(String message) { super(message); }
    public CheckApiException(String message, Throwable cause) { super(message, cause); }
}
```

```java
package com.koscom.kopilot.checkapi;

import java.time.LocalDate;
import java.util.List;

public interface CheckApiClient {
    List<DailyQuote> dailyQuotes(StockInfo instrument, LocalDate from, LocalDate to);
    List<NavQuote> etfNav(StockInfo etf, LocalDate from, LocalDate to);
}
```

- [ ] **Step 2: 픽스처 JSON 작성** (내부 표준 포맷 — 테스트·데모·계산 검증의 기준 데이터)

`src/main/resources/fixtures/daily-005930.json` (삼성전자, 5일, 기간수익률 정확히 +5.0%):

```json
{"quotes":[
 {"date":"2026-07-13","open":100,"high":101,"low":99,"close":100,"volume":1000},
 {"date":"2026-07-14","open":100,"high":102,"low":100,"close":101,"volume":1100},
 {"date":"2026-07-15","open":101,"high":103,"low":100,"close":102,"volume":1200},
 {"date":"2026-07-16","open":102,"high":104,"low":101,"close":103,"volume":1300},
 {"date":"2026-07-17","open":103,"high":106,"low":102,"close":105,"volume":1400}]}
```

`fixtures/daily-KOSPI.json` (코스피 지수, +2.0%):

```json
{"quotes":[
 {"date":"2026-07-13","open":200,"high":201,"low":199,"close":200,"volume":0},
 {"date":"2026-07-14","open":200,"high":202,"low":200,"close":201,"volume":0},
 {"date":"2026-07-15","open":201,"high":203,"low":200,"close":202,"volume":0},
 {"date":"2026-07-16","open":202,"high":204,"low":201,"close":203,"volume":0},
 {"date":"2026-07-17","open":203,"high":205,"low":202,"close":204,"volume":0}]}
```

`fixtures/daily-086520.json` (에코프로 — 변동성 테스트용, 일간수익률 +10%, −10%, +10%):

```json
{"quotes":[
 {"date":"2026-07-14","open":100,"high":100,"low":100,"close":100,"volume":500},
 {"date":"2026-07-15","open":100,"high":110,"low":100,"close":110,"volume":500},
 {"date":"2026-07-16","open":110,"high":110,"low":99,"close":99,"volume":500},
 {"date":"2026-07-17","open":99,"high":109,"low":99,"close":108.9,"volume":500}]}
```

`fixtures/daily-035720.json` (카카오 — 이격도: MA5=102, 현재가 104):

```json
{"quotes":[
 {"date":"2026-07-13","open":100,"high":100,"low":100,"close":100,"volume":300},
 {"date":"2026-07-14","open":100,"high":101,"low":100,"close":101,"volume":300},
 {"date":"2026-07-15","open":101,"high":102,"low":101,"close":102,"volume":300},
 {"date":"2026-07-16","open":102,"high":103,"low":102,"close":103,"volume":300},
 {"date":"2026-07-17","open":103,"high":104,"low":103,"close":104,"volume":300}]}
```

`fixtures/daily-066970.json` (엘앤에프, +2%): 2행 — close 200 → 204 (날짜 2026-07-16, 07-17).
`fixtures/daily-003670.json` (포스코퓨처엠, −1%): 2행 — close 100 → 99 (동일 날짜).
`fixtures/daily-005380.json` (현대차 — 기간요약: 최고가 110, 최저가 95, 수익률 +5%):

```json
{"quotes":[
 {"date":"2026-07-16","open":100,"high":102,"low":95,"close":100,"volume":700},
 {"date":"2026-07-17","open":101,"high":110,"low":99,"close":105,"volume":800}]}
```

`fixtures/nav-360750.json` (TIGER 미국S&P500 — 최신 괴리율 +1.0%, 평균 +0.5%):

```json
{"navs":[
 {"date":"2026-07-15","marketPrice":10000,"nav":10000},
 {"date":"2026-07-16","marketPrice":10050,"nav":10000},
 {"date":"2026-07-17","marketPrice":10100,"nav":10000}]}
```

(엘앤에프·포스코퓨처엠 파일은 위 JSON 형식과 동일하게 2행짜리로 작성. open/high/low는 close와 같은 값, volume 100으로 채운다.)

- [ ] **Step 3: 실패하는 테스트 작성**

`FixtureCheckApiClientTest.java`:

```java
package com.koscom.kopilot.checkapi;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class FixtureCheckApiClientTest {

    private final FixtureCheckApiClient client = new FixtureCheckApiClient();
    private final StockInfo samsung = new StockInfo("005930", "삼성전자", "KOSPI", "STOCK");
    private final StockInfo tigerSnp = new StockInfo("360750", "TIGER 미국S&P500", "KOSPI", "ETF");

    @Test
    void dailyQuotes_readsFixtureAndFiltersByDate() {
        List<DailyQuote> all = client.dailyQuotes(samsung,
                LocalDate.parse("2026-07-13"), LocalDate.parse("2026-07-17"));
        assertThat(all).hasSize(5);
        assertThat(all.get(0).close()).isEqualTo(100.0);
        assertThat(all.get(4).close()).isEqualTo(105.0);

        List<DailyQuote> partial = client.dailyQuotes(samsung,
                LocalDate.parse("2026-07-15"), LocalDate.parse("2026-07-17"));
        assertThat(partial).hasSize(3);
    }

    @Test
    void etfNav_readsNavFixture() {
        List<NavQuote> navs = client.etfNav(tigerSnp,
                LocalDate.parse("2026-07-15"), LocalDate.parse("2026-07-17"));
        assertThat(navs).hasSize(3);
        assertThat(navs.get(2).marketPrice()).isEqualTo(10100.0);
        assertThat(navs.get(2).nav()).isEqualTo(10000.0);
    }

    @Test
    void unknownSymbol_throwsCheckApiException() {
        StockInfo ghost = new StockInfo("999999", "없는종목", "KOSPI", "STOCK");
        assertThatThrownBy(() -> client.dailyQuotes(ghost,
                LocalDate.parse("2026-07-13"), LocalDate.parse("2026-07-17")))
                .isInstanceOf(CheckApiException.class);
    }
}
```

- [ ] **Step 4: 테스트 실패 확인**

```bash
cd check-kopilot/backend && ./gradlew test --tests '*FixtureCheckApiClientTest'
```

Expected: 컴파일 에러(`FixtureCheckApiClient` 미존재)로 FAIL.

- [ ] **Step 5: FixtureCheckApiClient 구현**

```java
package com.koscom.kopilot.checkapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** classpath:fixtures/daily-{code}.json / nav-{code}.json 을 읽는 구현. 테스트·데모 폴백용. */
public class FixtureCheckApiClient implements CheckApiClient {

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public List<DailyQuote> dailyQuotes(StockInfo instrument, LocalDate from, LocalDate to) {
        JsonNode root = read("fixtures/daily-" + instrument.code() + ".json");
        List<DailyQuote> result = new ArrayList<>();
        for (JsonNode q : root.get("quotes")) {
            LocalDate d = LocalDate.parse(q.get("date").asText());
            if (!d.isBefore(from) && !d.isAfter(to)) {
                result.add(new DailyQuote(d, q.get("open").asDouble(), q.get("high").asDouble(),
                        q.get("low").asDouble(), q.get("close").asDouble(), q.get("volume").asLong()));
            }
        }
        return result;
    }

    @Override
    public List<NavQuote> etfNav(StockInfo etf, LocalDate from, LocalDate to) {
        JsonNode root = read("fixtures/nav-" + etf.code() + ".json");
        List<NavQuote> result = new ArrayList<>();
        for (JsonNode n : root.get("navs")) {
            LocalDate d = LocalDate.parse(n.get("date").asText());
            if (!d.isBefore(from) && !d.isAfter(to)) {
                result.add(new NavQuote(d, n.get("marketPrice").asDouble(), n.get("nav").asDouble()));
            }
        }
        return result;
    }

    private JsonNode read(String path) {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(path)) {
            if (in == null) throw new CheckApiException("픽스처 없음: " + path);
            return mapper.readTree(in);
        } catch (java.io.IOException e) {
            throw new CheckApiException("픽스처 읽기 실패: " + path, e);
        }
    }
}
```

주의: 픽스처를 테스트 리소스(`src/test/resources`)에 두면 프로덕션 `fixture` 프로파일에서 못 읽는다. Step 2에서 명시한 대로 **`src/main/resources/fixtures/`에 둔다** — 테스트는 main 리소스를 classpath로 그대로 읽는다.

- [ ] **Step 6: 테스트 통과 확인**

```bash
./gradlew test --tests '*FixtureCheckApiClientTest'
```

Expected: BUILD SUCCESSFUL, 3 tests passed.

- [ ] **Step 7: RestCheckApiClient 구현 + 실 API 대조(수동)**

```java
package com.koscom.kopilot.checkapi;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 실제 CHECK API 호출 구현. 외부 명세 의존부는 이 클래스에만 존재한다.
 * 응답 필드명(quotes/date/open/... )은 가정값 — 실제 명세와 다르면 map* 메서드만 수정한다.
 * 지수 백오프 재시도 3회.
 */
public class RestCheckApiClient implements CheckApiClient {

    private static final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final RestClient rest;
    private final CheckApiProperties props;

    public RestCheckApiClient(CheckApiProperties props) {
        this.props = props;
        this.rest = RestClient.builder()
                .baseUrl(props.baseUrl())
                .defaultHeader("apiKey", props.apiKey())
                .build();
    }

    @Override
    public List<DailyQuote> dailyQuotes(StockInfo instrument, LocalDate from, LocalDate to) {
        String pathTemplate = instrument.isIndex()
                ? props.paths().get("index-daily") : props.paths().get("stock-daily");
        String path = pathTemplate.replace("{symbol}", instrument.code()).replace("{code}", instrument.code());
        JsonNode root = getWithRetry(path, from, to);
        return mapDaily(root);
    }

    @Override
    public List<NavQuote> etfNav(StockInfo etf, LocalDate from, LocalDate to) {
        String path = props.paths().get("etf-nav").replace("{symbol}", etf.code());
        JsonNode root = getWithRetry(path, from, to);
        return mapNav(root);
    }

    private JsonNode getWithRetry(String path, LocalDate from, LocalDate to) {
        RuntimeException last = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                return rest.get()
                        .uri(uri -> uri.path(path)
                                .queryParam("fromDate", from.format(YMD))
                                .queryParam("toDate", to.format(YMD))
                                .build())
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

    private List<DailyQuote> mapDaily(JsonNode root) {
        List<DailyQuote> out = new ArrayList<>();
        for (JsonNode q : root.path("quotes")) {
            out.add(new DailyQuote(parseDate(q.get("date").asText()),
                    q.get("open").asDouble(), q.get("high").asDouble(),
                    q.get("low").asDouble(), q.get("close").asDouble(),
                    q.path("volume").asLong()));
        }
        return out;
    }

    private List<NavQuote> mapNav(JsonNode root) {
        List<NavQuote> out = new ArrayList<>();
        for (JsonNode n : root.path("navs")) {
            out.add(new NavQuote(parseDate(n.get("date").asText()),
                    n.get("marketPrice").asDouble(), n.get("nav").asDouble()));
        }
        return out;
    }

    private LocalDate parseDate(String s) {
        return s.contains("-") ? LocalDate.parse(s) : LocalDate.parse(s, YMD);
    }
}
```

`CheckApiProperties.java`:

```java
package com.koscom.kopilot.checkapi;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.Map;

@ConfigurationProperties(prefix = "checkapi")
public record CheckApiProperties(String baseUrl, String apiKey, Map<String, String> paths) {}
```

(`KopilotApplication`에 `@ConfigurationPropertiesScan` 애노테이션 추가)

**실 API 대조(수동, 스펙 13절 리스크 #1 대응):** CHECK API 개발자센터 문서를 열고 (1) 일별 시세, (2) 지수 시세, (3) ETF NAV(iNAV) 엔드포인트의 실제 경로·인증 헤더·요청 파라미터·응답 필드명을 확인해 `application.yml`의 `checkapi.paths`·인증 헤더명과 `mapDaily`/`mapNav`를 수정한다. 확인 방법:

```bash
CHECK_API_KEY=... ./gradlew bootRun   # 임시로 아래 스모크 러너 활성화
```

스모크 러너(개발 중에만 사용, 커밋은 하되 기본 비활성):

```java
// checkapi/SmokeRunner.java — @Profile("smoke") 로만 실행
package com.koscom.kopilot.checkapi;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import java.time.LocalDate;

@Configuration
@Profile("smoke")
public class SmokeRunner {
    @Bean
    CommandLineRunner smoke(CheckApiClient client) {
        return args -> {
            var samsung = new StockInfo("005930", "삼성전자", "KOSPI", "STOCK");
            System.out.println(client.dailyQuotes(samsung,
                    LocalDate.now().minusDays(30), LocalDate.now()));
        };
    }
}
```

실행: `SPRING_PROFILES_ACTIVE=smoke CHECK_API_KEY=... ./gradlew bootRun` → 최근 한 달 시세 리스트가 출력되면 성공. **6개 지표가 필요로 하는 데이터(주식/지수 일별시세, ETF NAV)가 실제로 조회되는지 이 시점에 확인하고, 불가한 지표가 있으면 사용자에게 보고한다(스펙: 불가 지표는 교체).**

- [ ] **Step 8: Bean 구성** — `checkapi/CheckApiConfig.java`:

```java
package com.koscom.kopilot.checkapi;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
public class CheckApiConfig {

    @Bean
    @Profile("!fixture")
    public CheckApiClient restCheckApiClient(CheckApiProperties props) {
        return new RestCheckApiClient(props);
    }

    @Bean
    @Profile("fixture")
    public CheckApiClient fixtureCheckApiClient() {
        return new FixtureCheckApiClient();
    }
}
```

(`fixture` 프로파일로 앱을 띄우면 실 API 없이 전체 플로우 데모 가능 — Task 16에서 사용)

- [ ] **Step 9: 전체 테스트 + Commit**

```bash
./gradlew test
git add -A && git commit -m "feat: CHECK API client with rest and fixture implementations"
```
---

### Task 3: 응답 캐시(Redis TTL) + 장애 폴백(MySQL) + 종목 마스터(이름→코드 검색)

**Files:**
- Create: `backend/src/main/java/com/koscom/kopilot/checkapi/CachingCheckApiClient.java`
- Create: `backend/src/main/java/com/koscom/kopilot/checkapi/RedisCacheStore.java`, `JdbcFallbackStore.java`
- Create: `backend/src/main/java/com/koscom/kopilot/checkapi/StockResolver.java`, `JdbcStockResolver.java`, `StockMasterLoader.java`, `AmbiguousStockException.java`, `StockNotFoundException.java`
- Create: `backend/src/main/resources/stock-master.csv`
- Modify: `backend/src/main/java/com/koscom/kopilot/checkapi/CheckApiConfig.java` (캐시+폴백 래퍼 적용)
- Test: `backend/src/test/java/com/koscom/kopilot/checkapi/JdbcStockResolverTest.java`, `CachingCheckApiClientTest.java`

**Interfaces:**
- Consumes: Task 1 `check_fallback`/`stock_master` 테이블 + Redis, Task 2 `CheckApiClient`
- Produces:
  - `interface StockResolver { StockInfo resolve(String name); List<StockInfo> search(String name); }`
  - `class AmbiguousStockException extends RuntimeException` — 필드 `String query`, `List<StockInfo> candidates` (getter 동명)
  - `class StockNotFoundException extends RuntimeException` — 필드 `String query`, `List<StockInfo> suggestions`
  - 2계층 캐시 동작:
    - ① Redis 단기 캐시 hit → 즉시 반환(외부 호출 없음)
    - ② miss → 델리게이트 호출 성공 시 Redis(TTL) + MySQL `check_fallback`(무기한) 동시 기록
    - ③ 델리게이트 실패 시 MySQL 폴백 스냅샷으로 응답, 스냅샷도 없으면 예외 재전파

> 두 저장소를 나누는 이유: TTL 캐시는 만료되어야 하고, 스펙 10절의 데모 폴백 스냅샷은 절대 만료되면 안 된다. 한 저장소로 합치면 둘 중 하나를 포기하게 된다.

- [ ] **Step 1: 실패하는 테스트 작성**

`CachingCheckApiClientTest.java` (DB·Redis 불필요 — 저장소를 인터페이스로 분리해 인메모리 Map을 주입):

```java
package com.koscom.kopilot.checkapi;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;

class CachingCheckApiClientTest {

    /** Redis/MySQL 없이 단위 테스트하기 위한 인메모리 저장소 */
    static class MapStore implements CachingCheckApiClient.KeyValueStore {
        final Map<String, String> map = new HashMap<>();
        public void put(String key, String json) { map.put(key, json); }
        public Optional<String> get(String key) { return Optional.ofNullable(map.get(key)); }
    }

    static CheckApiClient broken() {
        return new CheckApiClient() {
            public List<DailyQuote> dailyQuotes(StockInfo i, LocalDate f, LocalDate t) {
                throw new CheckApiException("down");
            }
            public List<NavQuote> etfNav(StockInfo e, LocalDate f, LocalDate t) {
                throw new CheckApiException("down");
            }
        };
    }

    private final StockInfo samsung = new StockInfo("005930", "삼성전자", "KOSPI", "STOCK");
    private final LocalDate from = LocalDate.parse("2026-07-13");
    private final LocalDate to = LocalDate.parse("2026-07-17");

    @Test
    void success_populatesShortTermCacheAndFallbackSnapshot() {
        MapStore hot = new MapStore();
        MapStore cold = new MapStore();

        CachingCheckApiClient c = new CachingCheckApiClient(new FixtureCheckApiClient(), hot, cold);
        assertThat(c.dailyQuotes(samsung, from, to)).hasSize(5);
        assertThat(hot.map).isNotEmpty();
        assertThat(cold.map).isNotEmpty();
    }

    @Test
    void shortTermCacheHit_skipsDelegate() {
        MapStore hot = new MapStore();
        MapStore cold = new MapStore();
        new CachingCheckApiClient(new FixtureCheckApiClient(), hot, cold).dailyQuotes(samsung, from, to);

        // 델리게이트가 죽어도 단기 캐시가 살아 있으면 정상 응답
        CachingCheckApiClient c = new CachingCheckApiClient(broken(), hot, new MapStore());
        assertThat(c.dailyQuotes(samsung, from, to)).hasSize(5);
    }

    @Test
    void delegateFailure_fallsBackToPersistentSnapshot() {
        MapStore cold = new MapStore();
        new CachingCheckApiClient(new FixtureCheckApiClient(), new MapStore(), cold).dailyQuotes(samsung, from, to);

        // 단기 캐시는 비어 있고(TTL 만료 가정) 델리게이트도 실패 → MySQL 폴백 스냅샷 사용
        CachingCheckApiClient c = new CachingCheckApiClient(broken(), new MapStore(), cold);
        assertThat(c.dailyQuotes(samsung, from, to)).hasSize(5);
    }

    @Test
    void failureWithoutAnyCache_rethrows() {
        CachingCheckApiClient c = new CachingCheckApiClient(broken(), new MapStore(), new MapStore());
        assertThatThrownBy(() -> c.dailyQuotes(samsung, from, to))
                .isInstanceOf(CheckApiException.class);
    }
}
```

`JdbcStockResolverTest.java` — DB 연동 테스트. `@SpringBootTest` + 로컬 MySQL·Redis를 사용한다(간단 우선; docker compose가 떠 있어야 함):

```java
package com.koscom.kopilot.checkapi;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("fixture")
class JdbcStockResolverTest {

    @Autowired StockResolver resolver;

    @Test
    void exactName_resolvesUniquely() {
        StockInfo s = resolver.resolve("삼성전자");
        assertThat(s.code()).isEqualTo("005930");
    }

    @Test
    void indexName_resolves() {
        StockInfo s = resolver.resolve("코스피");
        assertThat(s.isIndex()).isTrue();
    }

    // "에코프로"는 에코프로/에코프로비엠/에코프로에이치엔 3건 부분매칭이지만
    // 정확일치 "에코프로"가 존재하므로 정확일치 우선. "에코"는 정확일치가 없어 Ambiguous.
    @Test
    void exactMatchWinsOverPartialMatches() {
        assertThat(resolver.resolve("에코프로").code()).isEqualTo("086520");
    }

    @Test
    void partialOnly_throwsAmbiguousWithCandidates() {
        assertThatThrownBy(() -> resolver.resolve("에코"))
                .isInstanceOfSatisfying(AmbiguousStockException.class,
                        e -> assertThat(e.candidates()).hasSizeGreaterThanOrEqualTo(3));
    }

    @Test
    void unknownName_throwsNotFound() {
        assertThatThrownBy(() -> resolver.resolve("없는회사12345"))
                .isInstanceOf(StockNotFoundException.class);
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

```bash
./gradlew test --tests '*CachingCheckApiClientTest' --tests '*JdbcStockResolverTest'
```

Expected: 컴파일 에러로 FAIL.

- [ ] **Step 3: 구현**

`stock-master.csv` (데모 종목 풀 — code,name,market,type):

```csv
005930,삼성전자,KOSPI,STOCK
005380,현대차,KOSPI,STOCK
035720,카카오,KOSPI,STOCK
086520,에코프로,KOSDAQ,STOCK
247540,에코프로비엠,KOSDAQ,STOCK
383310,에코프로에이치엔,KOSDAQ,STOCK
066970,엘앤에프,KOSDAQ,STOCK
003670,포스코퓨처엠,KOSPI,STOCK
373220,LG에너지솔루션,KOSPI,STOCK
069500,KODEX 200,KOSPI,ETF
360750,TIGER 미국S&P500,KOSPI,ETF
KOSPI,코스피,INDEX,INDEX
KOSDAQ,코스닥,INDEX,INDEX
```

(실 API 대조 후 데모에 쓸 종목을 자유롭게 추가한다. 전체 종목 마스터 API 연동은 로드맵 — MVP는 CSV 풀로 충분.)

`AmbiguousStockException.java`:

```java
package com.koscom.kopilot.checkapi;

import java.util.List;

public class AmbiguousStockException extends RuntimeException {
    private final String query;
    private final List<StockInfo> candidates;

    public AmbiguousStockException(String query, List<StockInfo> candidates) {
        super("종목명 다건 매칭: " + query);
        this.query = query;
        this.candidates = candidates;
    }
    public String query() { return query; }
    public List<StockInfo> candidates() { return candidates; }
}
```

`StockNotFoundException.java` (동일 구조 — `List<StockInfo> suggestions()` / `String query()`).

`StockResolver.java`:

```java
package com.koscom.kopilot.checkapi;

import java.util.List;

public interface StockResolver {
    /** 이름/코드로 단일 종목 확정. 다건이면 AmbiguousStockException, 0건이면 StockNotFoundException. */
    StockInfo resolve(String nameOrCode);
    List<StockInfo> search(String name);
}
```

`JdbcStockResolver.java`:

```java
package com.koscom.kopilot.checkapi;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class JdbcStockResolver implements StockResolver {

    private final JdbcTemplate jdbc;

    public JdbcStockResolver(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public StockInfo resolve(String nameOrCode) {
        String q = nameOrCode.trim();
        // 1) 코드 정확일치  2) 이름 정확일치  3) 부분일치
        List<StockInfo> byCode = query("SELECT * FROM stock_master WHERE code = ?", q);
        if (byCode.size() == 1) return byCode.get(0);
        List<StockInfo> exact = query("SELECT * FROM stock_master WHERE name = ?", q);
        if (exact.size() == 1) return exact.get(0);
        List<StockInfo> partial = search(q);
        if (partial.isEmpty()) {
            List<StockInfo> sugg = q.length() >= 2 ? search(q.substring(0, 2)) : List.of();
            throw new StockNotFoundException(q, sugg);
        }
        if (partial.size() == 1) return partial.get(0);
        throw new AmbiguousStockException(q, partial);
    }

    @Override
    public List<StockInfo> search(String name) {
        // MySQL 기본 콜레이션(utf8mb4_0900_ai_ci)은 대소문자를 구분하지 않으므로 LIKE로 충분하다.
        return query("SELECT * FROM stock_master WHERE name LIKE ? ORDER BY CHAR_LENGTH(name), name LIMIT 5",
                "%" + name.trim() + "%");
    }

    private List<StockInfo> query(String sql, Object... args) {
        return jdbc.query(sql, (rs, i) -> new StockInfo(
                rs.getString("code"), rs.getString("name"),
                rs.getString("market"), rs.getString("type")), args);
    }
}
```

`StockMasterLoader.java` (기동 시 테이블 비어 있으면 CSV 적재):

```java
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

    @Override
    public void run(String... args) throws Exception {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM stock_master", Integer.class);
        if (count != null && count > 0) return;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                new ClassPathResource("stock-master.csv").getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] f = line.split(",");
                jdbc.update("INSERT IGNORE INTO stock_master(code,name,market,type) VALUES (?,?,?,?)",
                        f[0].trim(), f[1].trim(), f[2].trim(), f[3].trim());
            }
        }
    }
}
```

`CachingCheckApiClient.java`:

```java
package com.koscom.kopilot.checkapi;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 2계층 캐시 래퍼.
 *  - shortTerm(Redis, TTL): 반복 질문의 지연·쿼터 절감. hit이면 외부 호출 자체를 생략한다.
 *  - fallback(MySQL, 무기한): CHECK API 장애 시 마지막 성공 응답으로 데모를 살린다(스펙 10절).
 */
public class CachingCheckApiClient implements CheckApiClient {

    /** Redis/JDBC 구현을 갈아끼우기 위한 최소 인터페이스 (단위 테스트는 Map 구현 주입) */
    public interface KeyValueStore {
        void put(String key, String json);
        Optional<String> get(String key);
    }

    private final CheckApiClient delegate;
    private final KeyValueStore shortTerm;
    private final KeyValueStore fallback;
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    public CachingCheckApiClient(CheckApiClient delegate, KeyValueStore shortTerm, KeyValueStore fallback) {
        this.delegate = delegate;
        this.shortTerm = shortTerm;
        this.fallback = fallback;
    }

    @Override
    public List<DailyQuote> dailyQuotes(StockInfo instrument, LocalDate from, LocalDate to) {
        String key = "daily:%s:%s:%s".formatted(instrument.code(), from, to);
        return fetch(key, () -> delegate.dailyQuotes(instrument, from, to),
                new TypeReference<List<DailyQuote>>() {});
    }

    @Override
    public List<NavQuote> etfNav(StockInfo etf, LocalDate from, LocalDate to) {
        String key = "nav:%s:%s:%s".formatted(etf.code(), from, to);
        return fetch(key, () -> delegate.etfNav(etf, from, to),
                new TypeReference<List<NavQuote>>() {});
    }

    private <T> T fetch(String key, java.util.function.Supplier<T> call, TypeReference<T> type) {
        Optional<String> hot = shortTerm.get(key);
        if (hot.isPresent()) return read(hot.get(), type);
        try {
            T fresh = call.get();
            String json = write(fresh);
            shortTerm.put(key, json);
            fallback.put(key, json);
            return fresh;
        } catch (RuntimeException e) {
            Optional<String> snapshot = fallback.get(key);
            if (snapshot.isPresent()) return read(snapshot.get(), type);
            throw e;
        }
    }

    private String write(Object o) {
        try { return mapper.writeValueAsString(o); }
        catch (Exception e) { throw new CheckApiException("캐시 직렬화 실패", e); }
    }

    private <T> T read(String json, TypeReference<T> type) {
        try { return mapper.readValue(json, type); }
        catch (Exception e) { throw new CheckApiException("캐시 역직렬화 실패", e); }
    }
}
```

`RedisCacheStore.java` (단기 캐시):

```java
package com.koscom.kopilot.checkapi;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Optional;

/** CHECK API 단기 응답 캐시 (Redis, TTL). 키 네임스페이스: checkapi: */
public class RedisCacheStore implements CachingCheckApiClient.KeyValueStore {

    private static final String PREFIX = "checkapi:";

    private final StringRedisTemplate redis;
    private final Duration ttl;

    public RedisCacheStore(StringRedisTemplate redis, Duration ttl) {
        this.redis = redis;
        this.ttl = ttl;
    }

    @Override
    public void put(String key, String json) {
        try { redis.opsForValue().set(PREFIX + key, json, ttl); }
        catch (RuntimeException ignored) { /* 캐시 장애가 본 기능을 막지 않는다 */ }
    }

    @Override
    public Optional<String> get(String key) {
        try { return Optional.ofNullable(redis.opsForValue().get(PREFIX + key)); }
        catch (RuntimeException e) { return Optional.empty(); }
    }
}
```

`JdbcFallbackStore.java` (영속 폴백 스냅샷):

```java
package com.koscom.kopilot.checkapi;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Optional;

/** CHECK API 장애 대비 영속 스냅샷 (MySQL check_fallback). 데모 종목 풀은 warmup 스크립트로 사전 적재한다. */
public class JdbcFallbackStore implements CachingCheckApiClient.KeyValueStore {

    private final JdbcTemplate jdbc;

    public JdbcFallbackStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public void put(String key, String json) {
        jdbc.update("""
            INSERT INTO check_fallback(cache_key, payload, fetched_at) VALUES (?, ?, CURRENT_TIMESTAMP)
            ON DUPLICATE KEY UPDATE payload = VALUES(payload), fetched_at = CURRENT_TIMESTAMP
            """, key, json);
    }

    @Override
    public Optional<String> get(String key) {
        return jdbc.queryForList("SELECT payload FROM check_fallback WHERE cache_key = ?", String.class, key)
                .stream().findFirst();
    }
}
```

`CheckApiConfig` 수정 — 두 저장소를 만들고 REST 클라이언트를 캐시로 감싼다:

```java
package com.koscom.kopilot.checkapi;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;

@Configuration
public class CheckApiConfig {

    @Bean
    public RedisCacheStore checkApiShortTermCache(StringRedisTemplate redis,
                                                  @Value("${kopilot.cache-ttl}") Duration ttl) {
        return new RedisCacheStore(redis, ttl);
    }

    @Bean
    public JdbcFallbackStore checkApiFallbackStore(JdbcTemplate jdbc) {
        return new JdbcFallbackStore(jdbc);
    }

    @Bean
    @Profile("!fixture")
    public CheckApiClient checkApiClient(CheckApiProperties props,
                                         RedisCacheStore shortTerm,
                                         JdbcFallbackStore fallback) {
        return new CachingCheckApiClient(new RestCheckApiClient(props), shortTerm, fallback);
    }

    @Bean
    @Profile("fixture")
    public CheckApiClient fixtureCheckApiClient() {
        return new FixtureCheckApiClient();
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

```bash
docker compose up -d   # (check-kopilot/ 에서) JdbcStockResolverTest는 로컬 MySQL·Redis 필요
cd backend && ./gradlew test
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat: redis ttl cache with mysql fallback snapshot and stock master resolution"
```
---

### Task 4: 도메인 모델(카드 스키마)과 계산 유틸

이 태스크가 **프론트-백엔드 계약(카드 JSON 스키마)** 을 확정한다. 이후 실행기·xlsx·프론트가 전부 이 스키마에 의존한다.

**Files:**
- Create: `backend/src/main/java/com/koscom/kopilot/domain/MetricResult.java`, `Calculations.java`, `MetricException.java`
- Test: `backend/src/test/java/com/koscom/kopilot/domain/CalculationsTest.java`

**Interfaces:**
- Consumes: Task 2 `DailyQuote`
- Produces:
  - `MetricResult` 및 중첩 레코드 (아래 전문) — 카드 JSON 스키마의 원천
  - `Calculations.periodReturnPct(List<DailyQuote>)`, `dailyReturns(List<DailyQuote>)`, `sampleStdDev(List<Double>)`, `annualizedVolPct(List<DailyQuote>)`, `movingAverage(List<DailyQuote>, int)` — 전부 `static double`/`static List<Double>`
  - `class MetricException extends RuntimeException` — `code()`(예: `PERIOD_INVERTED`, `DATA_INSUFFICIENT`, `NOT_ETF`), `message`

- [ ] **Step 1: 실패하는 테스트 작성** — `CalculationsTest.java`

```java
package com.koscom.kopilot.domain;

import com.koscom.kopilot.checkapi.DailyQuote;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.api.Assertions.within;

class CalculationsTest {

    private static DailyQuote q(String date, double close) {
        return new DailyQuote(LocalDate.parse(date), close, close, close, close, 100);
    }

    // 기대값 산출 근거(엑셀로 재검증 가능):
    // closes 100→105 : (105/100 − 1)×100 = 5.0%
    @Test
    void periodReturnPct() {
        List<DailyQuote> quotes = List.of(q("2026-07-14", 100), q("2026-07-15", 102), q("2026-07-17", 105));
        assertThat(Calculations.periodReturnPct(quotes)).isCloseTo(5.0, within(1e-9));
    }

    @Test
    void periodReturn_requiresAtLeastTwoRows() {
        assertThatThrownBy(() -> Calculations.periodReturnPct(List.of(q("2026-07-14", 100))))
                .isInstanceOfSatisfying(MetricException.class,
                        e -> assertThat(e.code()).isEqualTo("DATA_INSUFFICIENT"));
    }

    // closes 100,110,99,108.9 → 일간수익률 {+10%, −10%, +10%}
    // 표본표준편차 = sqrt(1/75) = 0.1154700538…, 연율화 = sqrt(252/75) = 1.8330302…
    @Test
    void annualizedVolatility() {
        List<DailyQuote> quotes = List.of(
                q("2026-07-14", 100), q("2026-07-15", 110),
                q("2026-07-16", 99), q("2026-07-17", 108.9));
        assertThat(Calculations.dailyReturns(quotes))
                .containsExactly(0.10, -0.09999999999999998, 0.09999999999999987);
        assertThat(Calculations.annualizedVolPct(quotes)).isCloseTo(183.30303, within(0.001));
    }

    // closes 100,101,102,103,104 → MA5 = 102.0
    @Test
    void movingAverage() {
        List<DailyQuote> quotes = List.of(
                q("2026-07-13", 100), q("2026-07-14", 101), q("2026-07-15", 102),
                q("2026-07-16", 103), q("2026-07-17", 104));
        assertThat(Calculations.movingAverage(quotes, 5)).isCloseTo(102.0, within(1e-9));
    }

    @Test
    void movingAverage_requiresEnoughRows() {
        assertThatThrownBy(() -> Calculations.movingAverage(List.of(q("2026-07-17", 100)), 5))
                .isInstanceOfSatisfying(MetricException.class,
                        e -> assertThat(e.code()).isEqualTo("DATA_INSUFFICIENT"));
    }
}
```

참고: `dailyReturns`의 부동소수 기대값이 환경에 따라 미세하게 다르면 `containsExactly` 대신 각 원소를 `isCloseTo(±0.1, within(1e-9))`로 검증하도록 바꾼다.

- [ ] **Step 2: 테스트 실패 확인**

```bash
./gradlew test --tests '*CalculationsTest'
```

Expected: 컴파일 에러로 FAIL.

- [ ] **Step 3: 구현**

`MetricException.java`:

```java
package com.koscom.kopilot.domain;

public class MetricException extends RuntimeException {
    private final String code;

    public MetricException(String code, String message) {
        super(message);
        this.code = code;
    }
    public String code() { return code; }
}
```

`Calculations.java`:

```java
package com.koscom.kopilot.domain;

import com.koscom.kopilot.checkapi.DailyQuote;

import java.util.ArrayList;
import java.util.List;

/** 모든 지표의 수치 계산. LLM은 절대 계산하지 않는다 — 여기서만 계산한다. */
public final class Calculations {

    public static final int TRADING_DAYS_PER_YEAR = 252;

    private Calculations() {}

    /** 기간수익률(%) = (마지막 종가 / 첫 종가 − 1) × 100 */
    public static double periodReturnPct(List<DailyQuote> quotes) {
        requireRows(quotes, 2);
        double first = quotes.get(0).close();
        double last = quotes.get(quotes.size() - 1).close();
        return (last / first - 1) * 100;
    }

    /** 일간수익률 r_i = c_i / c_{i-1} − 1 */
    public static List<Double> dailyReturns(List<DailyQuote> quotes) {
        requireRows(quotes, 2);
        List<Double> out = new ArrayList<>();
        for (int i = 1; i < quotes.size(); i++) {
            out.add(quotes.get(i).close() / quotes.get(i - 1).close() - 1);
        }
        return out;
    }

    /** 표본표준편차(n−1) */
    public static double sampleStdDev(List<Double> values) {
        if (values.size() < 2) throw new MetricException("DATA_INSUFFICIENT", "표준편차 계산에 최소 2개 수익률 필요");
        double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double ss = values.stream().mapToDouble(v -> (v - mean) * (v - mean)).sum();
        return Math.sqrt(ss / (values.size() - 1));
    }

    /** 연율화 변동성(%) = 일간수익률 표본표준편차 × √252 × 100 */
    public static double annualizedVolPct(List<DailyQuote> quotes) {
        return sampleStdDev(dailyReturns(quotes)) * Math.sqrt(TRADING_DAYS_PER_YEAR) * 100;
    }

    /** 마지막 n개 종가의 단순이동평균 */
    public static double movingAverage(List<DailyQuote> quotes, int n) {
        requireRows(quotes, n);
        return quotes.subList(quotes.size() - n, quotes.size())
                .stream().mapToDouble(DailyQuote::close).average().orElseThrow();
    }

    private static void requireRows(List<DailyQuote> quotes, int n) {
        if (quotes == null || quotes.size() < n) {
            throw new MetricException("DATA_INSUFFICIENT",
                    "데이터 부족: 최소 " + n + "개 시세 필요 (현재 " + (quotes == null ? 0 : quotes.size()) + "개)");
        }
    }
}
```

`MetricResult.java` — **카드 스키마 전문** (프론트 `types.ts`가 이것을 그대로 미러링):

```java
package com.koscom.kopilot.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 지표 답변 카드 1장의 전체 데이터. 프론트는 이 JSON을 직접 렌더한다(스펙 5절 원칙 1).
 * Jackson 직렬화 형태 예시:
 * {
 *   "cardId":"...", "metric":"RETURN_GAP", "title":"삼성전자 vs 코스피 수익률 갭 (최근 1개월)",
 *   "from":"2026-06-19","to":"2026-07-19",
 *   "targets":[{"code":"005930","name":"삼성전자"}],
 *   "headline":[{"label":"수익률 갭","value":3.0,"unit":"%p"}],
 *   "chart":{"chartType":"line","series":[{"name":"삼성전자","points":[{"label":"2026-06-19","value":0.0}]}]},
 *   "evidence":{"apiCalls":[{"api":"주식 일별 시세","request":"...","specUrl":"..."}],
 *               "rawData":[{"name":"삼성전자","rows":[{"date":"2026-06-19","value":81500.0}]}],
 *               "formula":"...","steps":[{"label":"...","detail":"..."}]}
 * }
 */
public record MetricResult(
        String cardId,
        String metric,
        String title,
        LocalDate from,
        LocalDate to,
        List<Target> targets,
        List<Headline> headline,
        ChartSpec chart,
        Evidence evidence
) {
    public record Target(String code, String name) {}
    public record Headline(String label, double value, String unit) {}

    public record ChartSpec(String chartType, List<Series> series) {   // chartType: line | bar
        public record Series(String name, List<Point> points) {}
        /** label: line 차트는 ISO 날짜 문자열, bar 차트는 카테고리명(종목명 등) */
        public record Point(String label, double value) {}
    }

    public record Evidence(List<ApiCall> apiCalls, List<RawSeries> rawData,
                           String formula, List<Step> steps) {
        public record ApiCall(String api, String request, String specUrl) {}
        public record RawSeries(String name, List<Row> rows) {}
        public record Row(LocalDate date, double value) {}
        public record Step(String label, String detail) {}
    }

    public static String newCardId() { return UUID.randomUUID().toString(); }
}
```

- [ ] **Step 4: 테스트 통과 확인**

```bash
./gradlew test --tests '*CalculationsTest'
```

Expected: BUILD SUCCESSFUL, 5 tests passed.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat: metric card schema and pure calculation utilities"
```
---

### Task 5: 실행기 프레임워크 + 지표① 수익률 갭

**Files:**
- Create: `backend/src/main/java/com/koscom/kopilot/catalog/MetricExecutor.java`, `ExecutorSupport.java`, `CatalogService.java`, `ReturnGapExecutor.java`
- Test: `backend/src/test/java/com/koscom/kopilot/catalog/ReturnGapExecutorTest.java`
- Test-support: `backend/src/test/java/com/koscom/kopilot/catalog/TestStocks.java`

**Interfaces:**
- Consumes: Task 2~4 전부
- Produces (이후 실행기 5종·디스패처가 사용):
  - `interface MetricExecutor { String toolName(); String description(); Map<String,Object> inputSchemaProperties(); List<String> requiredParams(); MetricResult execute(JsonNode args); }`
  - `class ExecutorSupport` — 공통 헬퍼: `resolveTarget(String)`, `parsePeriod(JsonNode)`(from/to 검증: 역전·미래 금지), `cumulativeReturnSeries(...)`, `closeSeries(...)`, `rawRows(...)`, `specUrl(String apiId)`
  - `class CatalogService { List<MetricExecutor> all(); MetricExecutor byName(String); }`
  - tool 파라미터 규약(모든 실행기 공통): 날짜는 ISO `"YYYY-MM-DD"` 문자열 `from`/`to`. 종목은 한글명 그대로 전달(코드 확정은 백엔드)
- `specUrl`은 Task 8의 `ApiSpecIndex`에서 가져온다. **Task 8 전까지는 `ExecutorSupport`가 임시로 하드코딩 Map을 쓰고, Task 8에서 `ApiSpecIndex` 주입으로 교체한다** (교체 지점은 `ExecutorSupport.specUrl` 한 곳). 하드코딩 Map의 키는 Task 8 `api-aliases.yaml`의 별칭과 일치시킨다 — `stock-daily`, `index-daily`, `kosdaq-daily`, `stock-investor`

- [ ] **Step 1: 실패하는 테스트 작성**

`TestStocks.java` (테스트 전용 인메모리 StockResolver):

```java
package com.koscom.kopilot.catalog;

import com.koscom.kopilot.checkapi.*;

import java.util.List;

public final class TestStocks {
    private TestStocks() {}

    public static final List<StockInfo> ALL = List.of(
            new StockInfo("005930", "삼성전자", "KOSPI", "STOCK"),
            new StockInfo("005380", "현대차", "KOSPI", "STOCK"),
            new StockInfo("035720", "카카오", "KOSPI", "STOCK"),
            new StockInfo("086520", "에코프로", "KOSDAQ", "STOCK"),
            new StockInfo("247540", "에코프로비엠", "KOSDAQ", "STOCK"),
            new StockInfo("066970", "엘앤에프", "KOSDAQ", "STOCK"),
            new StockInfo("003670", "포스코퓨처엠", "KOSPI", "STOCK"),
            new StockInfo("360750", "TIGER 미국S&P500", "KOSPI", "ETF"),
            new StockInfo("KOSPI", "코스피", "INDEX", "INDEX"));

    public static StockResolver resolver() {
        return new StockResolver() {
            public StockInfo resolve(String nameOrCode) {
                List<StockInfo> exact = ALL.stream()
                        .filter(s -> s.name().equals(nameOrCode) || s.code().equals(nameOrCode)).toList();
                if (exact.size() == 1) return exact.get(0);
                List<StockInfo> partial = search(nameOrCode);
                if (partial.size() == 1) return partial.get(0);
                if (partial.isEmpty()) throw new StockNotFoundException(nameOrCode, List.of());
                throw new AmbiguousStockException(nameOrCode, partial);
            }
            public List<StockInfo> search(String name) {
                return ALL.stream().filter(s -> s.name().contains(name)).toList();
            }
        };
    }
}
```

`ReturnGapExecutorTest.java`:

```java
package com.koscom.kopilot.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.koscom.kopilot.checkapi.FixtureCheckApiClient;
import com.koscom.kopilot.domain.MetricException;
import com.koscom.kopilot.domain.MetricResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.api.Assertions.within;

class ReturnGapExecutorTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final ExecutorSupport support =
            new ExecutorSupport(new FixtureCheckApiClient(), TestStocks.resolver());
    private final ReturnGapExecutor executor = new ReturnGapExecutor(support);

    // 픽스처: 삼성전자 100→105 (+5.0%), 코스피 200→204 (+2.0%) → 갭 = 3.0%p
    @Test
    void computesGapFromFixtures() throws Exception {
        var args = mapper.readTree("""
            {"target_a":"삼성전자","target_b":"코스피","from":"2026-07-13","to":"2026-07-17"}""");
        MetricResult r = executor.execute(args);

        assertThat(r.metric()).isEqualTo("RETURN_GAP");
        assertThat(r.headline()).extracting(MetricResult.Headline::label)
                .contains("삼성전자 기간수익률", "코스피 기간수익률", "수익률 갭");
        assertThat(headlineValue(r, "수익률 갭")).isCloseTo(3.0, within(1e-9));
        assertThat(headlineValue(r, "삼성전자 기간수익률")).isCloseTo(5.0, within(1e-9));
        // 근거 패널 필수 구성요소
        assertThat(r.evidence().apiCalls()).isNotEmpty();
        assertThat(r.evidence().rawData()).hasSize(2);
        assertThat(r.evidence().formula()).contains("기간수익률");
        assertThat(r.evidence().steps()).isNotEmpty();
        // 차트: 누적수익률 2개 시리즈
        assertThat(r.chart().chartType()).isEqualTo("line");
        assertThat(r.chart().series()).hasSize(2);
    }

    @Test
    void invertedPeriod_throwsMetricException() throws Exception {
        var args = mapper.readTree("""
            {"target_a":"삼성전자","target_b":"코스피","from":"2026-07-17","to":"2026-07-13"}""");
        assertThatThrownBy(() -> executor.execute(args))
                .isInstanceOfSatisfying(MetricException.class,
                        e -> assertThat(e.code()).isEqualTo("PERIOD_INVERTED"));
    }

    static double headlineValue(MetricResult r, String label) {
        return r.headline().stream().filter(h -> h.label().equals(label))
                .findFirst().orElseThrow().value();
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

```bash
./gradlew test --tests '*ReturnGapExecutorTest'
```

Expected: 컴파일 에러로 FAIL.

- [ ] **Step 3: 구현**

`MetricExecutor.java`:

```java
package com.koscom.kopilot.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.koscom.kopilot.domain.MetricResult;

import java.util.List;
import java.util.Map;

/** 지표 1종 = 구현 클래스 1개. 지표 추가 = 클래스 추가(스펙 7절). */
public interface MetricExecutor {
    String toolName();                              // 예: "return_gap" — Claude tool 이름
    String description();                           // Claude가 tool 선택에 사용 — 언제 쓰는지 명시
    Map<String, Object> inputSchemaProperties();    // JSON Schema properties 맵
    List<String> requiredParams();
    MetricResult execute(JsonNode args);            // 검증 실패 시 MetricException
}
```

`ExecutorSupport.java`:

```java
package com.koscom.kopilot.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.koscom.kopilot.checkapi.CheckApiClient;
import com.koscom.kopilot.checkapi.DailyQuote;
import com.koscom.kopilot.checkapi.StockInfo;
import com.koscom.kopilot.checkapi.StockResolver;
import com.koscom.kopilot.domain.MetricException;
import com.koscom.kopilot.domain.MetricResult;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** 실행기 공통 의존성·헬퍼. */
public class ExecutorSupport {

    public record Period(LocalDate from, LocalDate to) {}

    private final CheckApiClient checkApi;
    private final StockResolver stocks;

    // Task 8에서 ApiSpecIndex 주입으로 교체하는 임시 매핑 (URL은 CHECK API 문서 디렉토리의 실제 페이지로 교체)
    private static final Map<String, String> SPEC_URLS = Map.of(
            "stock-daily", "https://checkapi.koscom.co.kr/docs/stock-daily",
            "index-daily", "https://checkapi.koscom.co.kr/docs/index-daily",
            "etf-nav", "https://checkapi.koscom.co.kr/docs/etf-nav");

    public ExecutorSupport(CheckApiClient checkApi, StockResolver stocks) {
        this.checkApi = checkApi;
        this.stocks = stocks;
    }

    public CheckApiClient api() { return checkApi; }

    public StockInfo resolveTarget(String nameOrCode) { return stocks.resolve(nameOrCode); }

    /** from/to 파싱·검증: 필수, 역전 금지, to는 오늘 이하 */
    public Period parsePeriod(JsonNode args) {
        String f = args.path("from").asText(null);
        String t = args.path("to").asText(null);
        if (f == null || t == null) throw new MetricException("PERIOD_MISSING", "from/to 날짜가 필요합니다 (YYYY-MM-DD)");
        LocalDate from, to;
        try {
            from = LocalDate.parse(f);
            to = LocalDate.parse(t);
        } catch (Exception e) {
            throw new MetricException("PERIOD_INVALID", "날짜 형식 오류: " + f + " ~ " + t);
        }
        if (from.isAfter(to)) throw new MetricException("PERIOD_INVERTED", "시작일이 종료일보다 늦습니다: " + from + " > " + to);
        if (to.isAfter(LocalDate.now())) throw new MetricException("PERIOD_FUTURE", "미래 날짜는 조회할 수 없습니다: " + to);
        return new Period(from, to);
    }

    public String requiredText(JsonNode args, String field) {
        String v = args.path(field).asText(null);
        if (v == null || v.isBlank()) throw new MetricException("PARAM_MISSING", "필수 파라미터 누락: " + field);
        return v;
    }

    /** 누적수익률(%) 시계열: value_i = (close_i / close_0 − 1)×100 */
    public MetricResult.ChartSpec.Series cumulativeReturnSeries(String name, List<DailyQuote> quotes) {
        double base = quotes.get(0).close();
        List<MetricResult.ChartSpec.Point> pts = quotes.stream()
                .map(q -> new MetricResult.ChartSpec.Point(q.date().toString(), (q.close() / base - 1) * 100)).toList();
        return new MetricResult.ChartSpec.Series(name, pts);
    }

    public MetricResult.ChartSpec.Series closeSeries(String name, List<DailyQuote> quotes) {
        List<MetricResult.ChartSpec.Point> pts = quotes.stream()
                .map(q -> new MetricResult.ChartSpec.Point(q.date().toString(), q.close())).toList();
        return new MetricResult.ChartSpec.Series(name, pts);
    }

    /** 근거 패널 원본 수치(종가). 최대 60행으로 캡. */
    public MetricResult.Evidence.RawSeries rawRows(String name, List<DailyQuote> quotes) {
        List<MetricResult.Evidence.Row> rows = quotes.stream()
                .map(q -> new MetricResult.Evidence.Row(q.date(), q.close())).toList();
        if (rows.size() > 60) rows = rows.subList(rows.size() - 60, rows.size());
        return new MetricResult.Evidence.RawSeries(name, rows);
    }

    public MetricResult.Evidence.ApiCall apiCall(String apiId, String apiName, StockInfo inst, Period p) {
        return new MetricResult.Evidence.ApiCall(apiName,
                "%s(%s) %s ~ %s".formatted(inst.name(), inst.code(), p.from(), p.to()),
                specUrl(apiId));
    }

    public String specUrl(String apiId) { return SPEC_URLS.getOrDefault(apiId, ""); }

    public static String fmt(double v) { return String.format("%,.4f", v); }
}
```

`ReturnGapExecutor.java`:

```java
package com.koscom.kopilot.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.koscom.kopilot.checkapi.DailyQuote;
import com.koscom.kopilot.checkapi.StockInfo;
import com.koscom.kopilot.domain.Calculations;
import com.koscom.kopilot.domain.MetricResult;

import java.util.List;
import java.util.Map;

public class ReturnGapExecutor implements MetricExecutor {

    private final ExecutorSupport s;

    public ReturnGapExecutor(ExecutorSupport s) { this.s = s; }

    @Override public String toolName() { return "return_gap"; }

    @Override public String description() {
        return "두 대상(종목/지수/ETF)의 기간수익률 차이(수익률 갭)를 계산한다. "
             + "예: '삼성전자랑 코스피 최근 한 달 수익률 갭'. 두 대상의 수익률을 비교하는 질문에 사용.";
    }

    @Override public Map<String, Object> inputSchemaProperties() {
        return Map.of(
            "target_a", Map.of("type", "string", "description", "첫 번째 대상의 한글 종목명/지수명 (예: 삼성전자)"),
            "target_b", Map.of("type", "string", "description", "두 번째 대상의 한글 종목명/지수명 (예: 코스피)"),
            "from", Map.of("type", "string", "description", "조회 시작일 YYYY-MM-DD"),
            "to", Map.of("type", "string", "description", "조회 종료일 YYYY-MM-DD"));
    }

    @Override public List<String> requiredParams() { return List.of("target_a", "target_b", "from", "to"); }

    @Override public MetricResult execute(JsonNode args) {
        StockInfo a = s.resolveTarget(s.requiredText(args, "target_a"));
        StockInfo b = s.resolveTarget(s.requiredText(args, "target_b"));
        ExecutorSupport.Period p = s.parsePeriod(args);

        List<DailyQuote> qa = s.api().dailyQuotes(a, p.from(), p.to());
        List<DailyQuote> qb = s.api().dailyQuotes(b, p.from(), p.to());
        double ra = Calculations.periodReturnPct(qa);
        double rb = Calculations.periodReturnPct(qb);
        double gap = ra - rb;

        var evidence = new MetricResult.Evidence(
            List.of(s.apiCall(a.isIndex() ? "index-daily" : "stock-daily", "일별 시세 조회", a, p),
                    s.apiCall(b.isIndex() ? "index-daily" : "stock-daily", "일별 시세 조회", b, p)),
            List.of(s.rawRows(a.name(), qa), s.rawRows(b.name(), qb)),
            "기간수익률(%) = (기간 마지막 종가 / 기간 첫 종가 − 1) × 100 ; 수익률 갭(%p) = A 기간수익률 − B 기간수익률",
            List.of(
                new MetricResult.Evidence.Step(a.name() + " 기간수익률",
                    "(%s / %s − 1) × 100 = %s%%".formatted(
                        ExecutorSupport.fmt(qa.get(qa.size() - 1).close()),
                        ExecutorSupport.fmt(qa.get(0).close()), ExecutorSupport.fmt(ra))),
                new MetricResult.Evidence.Step(b.name() + " 기간수익률",
                    "(%s / %s − 1) × 100 = %s%%".formatted(
                        ExecutorSupport.fmt(qb.get(qb.size() - 1).close()),
                        ExecutorSupport.fmt(qb.get(0).close()), ExecutorSupport.fmt(rb))),
                new MetricResult.Evidence.Step("수익률 갭",
                    "%s − %s = %s%%p".formatted(ExecutorSupport.fmt(ra), ExecutorSupport.fmt(rb), ExecutorSupport.fmt(gap)))));

        return new MetricResult(
            MetricResult.newCardId(), "RETURN_GAP",
            "%s vs %s 수익률 갭 (%s ~ %s)".formatted(a.name(), b.name(), p.from(), p.to()),
            p.from(), p.to(),
            List.of(new MetricResult.Target(a.code(), a.name()), new MetricResult.Target(b.code(), b.name())),
            List.of(new MetricResult.Headline(a.name() + " 기간수익률", round4(ra), "%"),
                    new MetricResult.Headline(b.name() + " 기간수익률", round4(rb), "%"),
                    new MetricResult.Headline("수익률 갭", round4(gap), "%p")),
            new MetricResult.ChartSpec("line",
                List.of(s.cumulativeReturnSeries(a.name(), qa), s.cumulativeReturnSeries(b.name(), qb))),
            evidence);
    }

    static double round4(double v) { return Math.round(v * 10000.0) / 10000.0; }
}
```

`CatalogService.java`:

```java
package com.koscom.kopilot.catalog;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CatalogService {

    private final List<MetricExecutor> executors;

    public CatalogService(List<MetricExecutor> executors) { this.executors = executors; }

    public List<MetricExecutor> all() { return executors; }

    public MetricExecutor byName(String toolName) {
        return executors.stream().filter(e -> e.toolName().equals(toolName)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("알 수 없는 지표 tool: " + toolName));
    }
}
```

Bean 등록 — `catalog/CatalogConfig.java`:

```java
package com.koscom.kopilot.catalog;

import com.koscom.kopilot.checkapi.CheckApiClient;
import com.koscom.kopilot.checkapi.StockResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CatalogConfig {

    @Bean public ExecutorSupport executorSupport(CheckApiClient api, StockResolver stocks) {
        return new ExecutorSupport(api, stocks);
    }

    @Bean public ReturnGapExecutor returnGapExecutor(ExecutorSupport s) { return new ReturnGapExecutor(s); }
    // Task 6~7에서 나머지 5종 실행기 Bean을 여기에 추가
}
```

- [ ] **Step 4: 테스트 통과 확인**

```bash
./gradlew test --tests '*ReturnGapExecutorTest'
```

Expected: BUILD SUCCESSFUL, 2 tests passed.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat: metric executor framework and return gap executor"
```
---

### Task 6: 지표② 변동성 비교 + 지표⑥ 기간 시세 요약

**Files:**
- Create: `backend/src/main/java/com/koscom/kopilot/catalog/VolatilityExecutor.java`, `PeriodSummaryExecutor.java`
- Modify: `backend/src/main/java/com/koscom/kopilot/catalog/CatalogConfig.java` (Bean 2개 추가)
- Test: `backend/src/test/java/com/koscom/kopilot/catalog/VolatilityExecutorTest.java`, `PeriodSummaryExecutorTest.java`

**Interfaces:**
- Consumes: Task 5 `MetricExecutor`/`ExecutorSupport`, Task 2 픽스처(`daily-086520.json`, `daily-005380.json`)
- Produces: tool `volatility`(params: `targets` string 배열 2~5개, `from`, `to`), tool `period_summary`(params: `target`, `from`, `to`)

- [ ] **Step 1: 실패하는 테스트 작성**

`VolatilityExecutorTest.java`:

```java
package com.koscom.kopilot.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.koscom.kopilot.checkapi.FixtureCheckApiClient;
import com.koscom.kopilot.domain.MetricResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.api.Assertions.within;
import static com.koscom.kopilot.catalog.ReturnGapExecutorTest.headlineValue;

class VolatilityExecutorTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final ExecutorSupport support =
            new ExecutorSupport(new FixtureCheckApiClient(), TestStocks.resolver());
    private final VolatilityExecutor executor = new VolatilityExecutor(support);

    // 에코프로 픽스처: 일간수익률 {+10%, −10%, +10%} → 연율화 변동성 = √(252/75)×100 = 183.30303%
    // 삼성전자 픽스처: closes 100,101,102,103,105 (계산은 코드가 수행 — 값 존재만 확인)
    @Test
    void computesAnnualizedVolatilityPerTarget() throws Exception {
        var args = mapper.readTree("""
            {"targets":["에코프로","삼성전자"],"from":"2026-07-13","to":"2026-07-17"}""");
        MetricResult r = executor.execute(args);

        assertThat(r.metric()).isEqualTo("VOLATILITY");
        assertThat(headlineValue(r, "에코프로 연율화 변동성")).isCloseTo(183.30303, within(0.001));
        assertThat(r.chart().chartType()).isEqualTo("bar");
        assertThat(r.chart().series()).hasSize(1);
        assertThat(r.chart().series().get(0).points()).hasSize(2);
        assertThat(r.evidence().formula()).contains("√252");
    }
}
```

`PeriodSummaryExecutorTest.java`:

```java
package com.koscom.kopilot.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.koscom.kopilot.checkapi.FixtureCheckApiClient;
import com.koscom.kopilot.domain.MetricResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.api.Assertions.within;
import static com.koscom.kopilot.catalog.ReturnGapExecutorTest.headlineValue;

class PeriodSummaryExecutorTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final ExecutorSupport support =
            new ExecutorSupport(new FixtureCheckApiClient(), TestStocks.resolver());
    private final PeriodSummaryExecutor executor = new PeriodSummaryExecutor(support);

    // 현대차 픽스처: high 최대 110, low 최소 95, close 100→105 (+5.0%)
    @Test
    void summarizesOhlcOverPeriod() throws Exception {
        var args = mapper.readTree("""
            {"target":"현대차","from":"2026-07-16","to":"2026-07-17"}""");
        MetricResult r = executor.execute(args);

        assertThat(r.metric()).isEqualTo("PERIOD_SUMMARY");
        assertThat(headlineValue(r, "기간 최고가")).isCloseTo(110.0, within(1e-9));
        assertThat(headlineValue(r, "기간 최저가")).isCloseTo(95.0, within(1e-9));
        assertThat(headlineValue(r, "기간수익률")).isCloseTo(5.0, within(1e-9));
        assertThat(r.chart().chartType()).isEqualTo("line");
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

```bash
./gradlew test --tests '*VolatilityExecutorTest' --tests '*PeriodSummaryExecutorTest'
```

Expected: 컴파일 에러로 FAIL.

- [ ] **Step 3: 구현**

`VolatilityExecutor.java`:

```java
package com.koscom.kopilot.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.koscom.kopilot.checkapi.DailyQuote;
import com.koscom.kopilot.checkapi.StockInfo;
import com.koscom.kopilot.domain.Calculations;
import com.koscom.kopilot.domain.MetricException;
import com.koscom.kopilot.domain.MetricResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class VolatilityExecutor implements MetricExecutor {

    private final ExecutorSupport s;

    public VolatilityExecutor(ExecutorSupport s) { this.s = s; }

    @Override public String toolName() { return "volatility"; }

    @Override public String description() {
        return "대상(1~5개)의 변동성(일간수익률 표준편차의 연율화)을 계산한다. 2개 이상이면 비교. "
             + "예: '에코프로랑 에코프로비엠 변동성 비교', '삼성전자 최근 3개월 변동성'. 변동성·위험도 질문에 사용.";
    }

    @Override public Map<String, Object> inputSchemaProperties() {
        return Map.of(
            "targets", Map.of("type", "array", "items", Map.of("type", "string"),
                    "description", "변동성을 계산할 대상들의 한글 종목명/지수명 (1~5개)"),
            "from", Map.of("type", "string", "description", "조회 시작일 YYYY-MM-DD"),
            "to", Map.of("type", "string", "description", "조회 종료일 YYYY-MM-DD"));
    }

    @Override public List<String> requiredParams() { return List.of("targets", "from", "to"); }

    @Override public MetricResult execute(JsonNode args) {
        JsonNode targetsNode = args.path("targets");
        if (!targetsNode.isArray() || targetsNode.size() < 1 || targetsNode.size() > 5) {
            throw new MetricException("PARAM_INVALID", "targets는 1~5개 대상 배열이어야 합니다");
        }
        ExecutorSupport.Period p = s.parsePeriod(args);

        List<MetricResult.Target> targets = new ArrayList<>();
        List<MetricResult.Headline> headline = new ArrayList<>();
        List<MetricResult.ChartSpec.Point> barPoints = new ArrayList<>();
        List<MetricResult.Evidence.ApiCall> apiCalls = new ArrayList<>();
        List<MetricResult.Evidence.RawSeries> raw = new ArrayList<>();
        List<MetricResult.Evidence.Step> steps = new ArrayList<>();

        for (JsonNode t : targetsNode) {
            StockInfo info = s.resolveTarget(t.asText());
            List<DailyQuote> quotes = s.api().dailyQuotes(info, p.from(), p.to());
            double dailyStd = Calculations.sampleStdDev(Calculations.dailyReturns(quotes));
            double annPct = dailyStd * Math.sqrt(Calculations.TRADING_DAYS_PER_YEAR) * 100;

            targets.add(new MetricResult.Target(info.code(), info.name()));
            headline.add(new MetricResult.Headline(info.name() + " 연율화 변동성",
                    ReturnGapExecutor.round4(annPct), "%"));
            barPoints.add(new MetricResult.ChartSpec.Point(info.name(), ReturnGapExecutor.round4(annPct)));
            apiCalls.add(s.apiCall(info.isIndex() ? "index-daily" : "stock-daily", "일별 시세 조회", info, p));
            raw.add(s.rawRows(info.name(), quotes));
            steps.add(new MetricResult.Evidence.Step(info.name(),
                    "일간수익률 표준편차 %s × √252 × 100 = %s%%".formatted(
                            ExecutorSupport.fmt(dailyStd), ExecutorSupport.fmt(annPct))));
        }

        return new MetricResult(
            MetricResult.newCardId(), "VOLATILITY",
            "변동성 비교 (%s ~ %s)".formatted(p.from(), p.to()),
            p.from(), p.to(), targets, headline,
            new MetricResult.ChartSpec("bar",
                List.of(new MetricResult.ChartSpec.Series("연율화 변동성(%)", barPoints))),
            new MetricResult.Evidence(apiCalls, raw,
                "연율화 변동성(%) = 일간수익률(cᵢ/cᵢ₋₁ − 1)의 표본표준편차(n−1) × √252 × 100",
                steps));
    }
}
```

`PeriodSummaryExecutor.java`:

```java
package com.koscom.kopilot.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.koscom.kopilot.checkapi.DailyQuote;
import com.koscom.kopilot.checkapi.StockInfo;
import com.koscom.kopilot.domain.Calculations;
import com.koscom.kopilot.domain.MetricResult;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class PeriodSummaryExecutor implements MetricExecutor {

    private final ExecutorSupport s;

    public PeriodSummaryExecutor(ExecutorSupport s) { this.s = s; }

    @Override public String toolName() { return "period_summary"; }

    @Override public String description() {
        return "한 대상의 기간 시세 요약(기간 최고가/최저가/기간수익률)을 계산한다. "
             + "예: '현대차 올해 최고가·최저가·수익률'. 특정 종목의 기간 시세 집계 질문에 사용.";
    }

    @Override public Map<String, Object> inputSchemaProperties() {
        return Map.of(
            "target", Map.of("type", "string", "description", "대상의 한글 종목명/지수명"),
            "from", Map.of("type", "string", "description", "조회 시작일 YYYY-MM-DD"),
            "to", Map.of("type", "string", "description", "조회 종료일 YYYY-MM-DD"));
    }

    @Override public List<String> requiredParams() { return List.of("target", "from", "to"); }

    @Override public MetricResult execute(JsonNode args) {
        StockInfo info = s.resolveTarget(s.requiredText(args, "target"));
        ExecutorSupport.Period p = s.parsePeriod(args);

        List<DailyQuote> quotes = s.api().dailyQuotes(info, p.from(), p.to());
        DailyQuote maxHigh = quotes.stream().max(Comparator.comparingDouble(DailyQuote::high)).orElseThrow();
        DailyQuote minLow = quotes.stream().min(Comparator.comparingDouble(DailyQuote::low)).orElseThrow();
        double ret = Calculations.periodReturnPct(quotes);

        return new MetricResult(
            MetricResult.newCardId(), "PERIOD_SUMMARY",
            "%s 기간 시세 요약 (%s ~ %s)".formatted(info.name(), p.from(), p.to()),
            p.from(), p.to(),
            List.of(new MetricResult.Target(info.code(), info.name())),
            List.of(new MetricResult.Headline("기간 최고가", maxHigh.high(), "원"),
                    new MetricResult.Headline("기간 최저가", minLow.low(), "원"),
                    new MetricResult.Headline("기간수익률", ReturnGapExecutor.round4(ret), "%")),
            new MetricResult.ChartSpec("line", List.of(s.closeSeries(info.name(), quotes))),
            new MetricResult.Evidence(
                List.of(s.apiCall(info.isIndex() ? "index-daily" : "stock-daily", "일별 시세 조회", info, p)),
                List.of(s.rawRows(info.name(), quotes)),
                "기간 최고가 = max(일별 고가), 기간 최저가 = min(일별 저가), 기간수익률(%) = (마지막 종가/첫 종가 − 1)×100",
                List.of(
                    new MetricResult.Evidence.Step("기간 최고가", "%s (%s)".formatted(
                            ExecutorSupport.fmt(maxHigh.high()), maxHigh.date())),
                    new MetricResult.Evidence.Step("기간 최저가", "%s (%s)".formatted(
                            ExecutorSupport.fmt(minLow.low()), minLow.date())),
                    new MetricResult.Evidence.Step("기간수익률", "(%s / %s − 1) × 100 = %s%%".formatted(
                            ExecutorSupport.fmt(quotes.get(quotes.size() - 1).close()),
                            ExecutorSupport.fmt(quotes.get(0).close()), ExecutorSupport.fmt(ret))))));
    }
}
```

`CatalogConfig`에 Bean 추가:

```java
    @Bean public VolatilityExecutor volatilityExecutor(ExecutorSupport s) { return new VolatilityExecutor(s); }
    @Bean public PeriodSummaryExecutor periodSummaryExecutor(ExecutorSupport s) { return new PeriodSummaryExecutor(s); }
```

- [ ] **Step 4: 테스트 통과 확인**

```bash
./gradlew test --tests '*VolatilityExecutorTest' --tests '*PeriodSummaryExecutorTest'
```

Expected: BUILD SUCCESSFUL, 2 tests passed.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat: volatility comparison and period summary executors"
```
---

### Task 7: 지표③ ETF 괴리율 + 지표④ 이동평균 이격도 + 지표⑤ 상대수익률 랭킹

**Files:**
- Create: `backend/src/main/java/com/koscom/kopilot/catalog/NavDisparityExecutor.java`, `MaDisparityExecutor.java`, `ReturnRankingExecutor.java`
- Modify: `backend/src/main/java/com/koscom/kopilot/catalog/CatalogConfig.java` (Bean 3개 추가)
- Test: `backend/src/test/java/com/koscom/kopilot/catalog/NavDisparityExecutorTest.java`, `MaDisparityExecutorTest.java`, `ReturnRankingExecutorTest.java`

**Interfaces:**
- Consumes: Task 5 프레임워크, Task 2 픽스처(`nav-360750.json`, `daily-035720.json`, `daily-086520/066970/003670.json`)
- Produces: tool `nav_disparity`(params: `target`, `from`, `to`), tool `ma_disparity`(params: `target`, `window` integer 기본 20, `as_of` 선택), tool `return_ranking`(params: `targets` 2~10개, `from`, `to`)

- [ ] **Step 1: 실패하는 테스트 작성**

`NavDisparityExecutorTest.java`:

```java
package com.koscom.kopilot.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.koscom.kopilot.checkapi.FixtureCheckApiClient;
import com.koscom.kopilot.domain.MetricException;
import com.koscom.kopilot.domain.MetricResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.api.Assertions.within;
import static com.koscom.kopilot.catalog.ReturnGapExecutorTest.headlineValue;

class NavDisparityExecutorTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final ExecutorSupport support =
            new ExecutorSupport(new FixtureCheckApiClient(), TestStocks.resolver());
    private final NavDisparityExecutor executor = new NavDisparityExecutor(support);

    // 픽스처: (10000,10000),(10050,10000),(10100,10000) → 최신 +1.0%, 기간평균 +0.5%
    @Test
    void computesDisparityFromNav() throws Exception {
        var args = mapper.readTree("""
            {"target":"TIGER 미국S&P500","from":"2026-07-15","to":"2026-07-17"}""");
        MetricResult r = executor.execute(args);

        assertThat(r.metric()).isEqualTo("NAV_DISPARITY");
        assertThat(headlineValue(r, "최신 괴리율")).isCloseTo(1.0, within(1e-9));
        assertThat(headlineValue(r, "기간 평균 괴리율")).isCloseTo(0.5, within(1e-9));
        assertThat(r.chart().chartType()).isEqualTo("line");
    }

    @Test
    void nonEtfTarget_throwsNotEtf() throws Exception {
        var args = mapper.readTree("""
            {"target":"삼성전자","from":"2026-07-15","to":"2026-07-17"}""");
        assertThatThrownBy(() -> executor.execute(args))
                .isInstanceOfSatisfying(MetricException.class,
                        e -> assertThat(e.code()).isEqualTo("NOT_ETF"));
    }
}
```

`MaDisparityExecutorTest.java`:

```java
package com.koscom.kopilot.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.koscom.kopilot.checkapi.FixtureCheckApiClient;
import com.koscom.kopilot.domain.MetricResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.api.Assertions.within;
import static com.koscom.kopilot.catalog.ReturnGapExecutorTest.headlineValue;

class MaDisparityExecutorTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final ExecutorSupport support =
            new ExecutorSupport(new FixtureCheckApiClient(), TestStocks.resolver());
    private final MaDisparityExecutor executor = new MaDisparityExecutor(support);

    // 카카오 픽스처: closes 100..104 → MA5 = 102, 이격도 = (104/102 − 1)×100 = 1.960784…%
    @Test
    void computesDisparityAgainstMovingAverage() throws Exception {
        var args = mapper.readTree("""
            {"target":"카카오","window":5,"as_of":"2026-07-17"}""");
        MetricResult r = executor.execute(args);

        assertThat(r.metric()).isEqualTo("MA_DISPARITY");
        assertThat(headlineValue(r, "5일선 이격도")).isCloseTo(1.960784, within(1e-4));
        assertThat(headlineValue(r, "현재가")).isCloseTo(104.0, within(1e-9));
        assertThat(headlineValue(r, "5일 이동평균")).isCloseTo(102.0, within(1e-9));
    }
}
```

`ReturnRankingExecutorTest.java`:

```java
package com.koscom.kopilot.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.koscom.kopilot.checkapi.FixtureCheckApiClient;
import com.koscom.kopilot.domain.MetricResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class ReturnRankingExecutorTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final ExecutorSupport support =
            new ExecutorSupport(new FixtureCheckApiClient(), TestStocks.resolver());
    private final ReturnRankingExecutor executor = new ReturnRankingExecutor(support);

    // 픽스처 수익률: 에코프로 픽스처(100→108.9, +8.9%), 엘앤에프(200→204, +2%), 포스코퓨처엠(100→99, −1%)
    @Test
    void ranksTargetsByPeriodReturnDescending() throws Exception {
        var args = mapper.readTree("""
            {"targets":["에코프로","엘앤에프","포스코퓨처엠"],"from":"2026-07-14","to":"2026-07-17"}""");
        MetricResult r = executor.execute(args);

        assertThat(r.metric()).isEqualTo("RETURN_RANKING");
        // headline은 수익률 내림차순으로 "1위 …" 형식
        assertThat(r.headline().get(0).label()).startsWith("1위 에코프로");
        assertThat(r.headline().get(1).label()).startsWith("2위 엘앤에프");
        assertThat(r.headline().get(2).label()).startsWith("3위 포스코퓨처엠");
        assertThat(r.chart().chartType()).isEqualTo("bar");
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

```bash
./gradlew test --tests '*NavDisparityExecutorTest' --tests '*MaDisparityExecutorTest' --tests '*ReturnRankingExecutorTest'
```

Expected: 컴파일 에러로 FAIL.

- [ ] **Step 3: 구현**

`NavDisparityExecutor.java`:

```java
package com.koscom.kopilot.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.koscom.kopilot.checkapi.NavQuote;
import com.koscom.kopilot.checkapi.StockInfo;
import com.koscom.kopilot.domain.Calculations;
import com.koscom.kopilot.domain.MetricException;
import com.koscom.kopilot.domain.MetricResult;

import java.util.List;
import java.util.Map;

public class NavDisparityExecutor implements MetricExecutor {

    private final ExecutorSupport s;

    public NavDisparityExecutor(ExecutorSupport s) { this.s = s; }

    @Override public String toolName() { return "nav_disparity"; }

    @Override public String description() {
        return "ETF의 괴리율(시장가 vs NAV)을 계산한다. 예: 'TIGER 미국S&P500 괴리율'. "
             + "ETF 전용 지표 — 일반 주식/지수에는 사용 불가.";
    }

    @Override public Map<String, Object> inputSchemaProperties() {
        return Map.of(
            "target", Map.of("type", "string", "description", "ETF의 한글 상품명 (예: TIGER 미국S&P500)"),
            "from", Map.of("type", "string", "description", "조회 시작일 YYYY-MM-DD"),
            "to", Map.of("type", "string", "description", "조회 종료일 YYYY-MM-DD"));
    }

    @Override public List<String> requiredParams() { return List.of("target", "from", "to"); }

    @Override public MetricResult execute(JsonNode args) {
        StockInfo info = s.resolveTarget(s.requiredText(args, "target"));
        if (!info.isEtf()) {
            throw new MetricException("NOT_ETF",
                    info.name() + "은(는) ETF가 아닙니다. 괴리율은 ETF 전용 지표입니다.");
        }
        ExecutorSupport.Period p = s.parsePeriod(args);

        List<NavQuote> navs = s.api().etfNav(info, p.from(), p.to());
        if (navs.isEmpty()) throw new MetricException("DATA_INSUFFICIENT", "NAV 데이터가 없습니다");

        List<MetricResult.ChartSpec.Point> series = navs.stream()
                .map(n -> new MetricResult.ChartSpec.Point(n.date().toString(), disparityPct(n))).toList();
        NavQuote latest = navs.get(navs.size() - 1);
        double latestDisp = disparityPct(latest);
        double avgDisp = navs.stream().mapToDouble(NavDisparityExecutor::disparityPct).average().orElseThrow();

        return new MetricResult(
            MetricResult.newCardId(), "NAV_DISPARITY",
            "%s 괴리율 (%s ~ %s)".formatted(info.name(), p.from(), p.to()),
            p.from(), p.to(),
            List.of(new MetricResult.Target(info.code(), info.name())),
            List.of(new MetricResult.Headline("최신 괴리율", ReturnGapExecutor.round4(latestDisp), "%"),
                    new MetricResult.Headline("기간 평균 괴리율", ReturnGapExecutor.round4(avgDisp), "%")),
            new MetricResult.ChartSpec("line",
                List.of(new MetricResult.ChartSpec.Series("괴리율(%)", series))),
            new MetricResult.Evidence(
                List.of(s.apiCall("etf-nav", "ETF NAV 조회", info, p)),
                List.of(new MetricResult.Evidence.RawSeries(info.name() + " 시장가",
                            navs.stream().map(n -> new MetricResult.Evidence.Row(n.date(), n.marketPrice())).toList()),
                        new MetricResult.Evidence.RawSeries(info.name() + " NAV",
                            navs.stream().map(n -> new MetricResult.Evidence.Row(n.date(), n.nav())).toList())),
                "괴리율(%) = (시장가 − NAV) / NAV × 100",
                List.of(new MetricResult.Evidence.Step("최신 괴리율 (" + latest.date() + ")",
                        "(%s − %s) / %s × 100 = %s%%".formatted(
                                ExecutorSupport.fmt(latest.marketPrice()), ExecutorSupport.fmt(latest.nav()),
                                ExecutorSupport.fmt(latest.nav()), ExecutorSupport.fmt(latestDisp))))));
    }

    static double disparityPct(NavQuote n) { return (n.marketPrice() - n.nav()) / n.nav() * 100; }
}
```

`MaDisparityExecutor.java`:

```java
package com.koscom.kopilot.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.koscom.kopilot.checkapi.DailyQuote;
import com.koscom.kopilot.checkapi.StockInfo;
import com.koscom.kopilot.domain.Calculations;
import com.koscom.kopilot.domain.MetricException;
import com.koscom.kopilot.domain.MetricResult;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public class MaDisparityExecutor implements MetricExecutor {

    private final ExecutorSupport s;

    public MaDisparityExecutor(ExecutorSupport s) { this.s = s; }

    @Override public String toolName() { return "ma_disparity"; }

    @Override public String description() {
        return "종목의 N일 이동평균선 대비 이격도를 계산한다. 예: '카카오 20일선 이격도'. "
             + "이동평균선·이격도 질문에 사용. window 기본값 20.";
    }

    @Override public Map<String, Object> inputSchemaProperties() {
        return Map.of(
            "target", Map.of("type", "string", "description", "대상의 한글 종목명"),
            "window", Map.of("type", "integer", "description", "이동평균 일수 (기본 20)"),
            "as_of", Map.of("type", "string", "description", "기준일 YYYY-MM-DD (생략 시 오늘)"));
    }

    @Override public List<String> requiredParams() { return List.of("target"); }

    @Override public MetricResult execute(JsonNode args) {
        StockInfo info = s.resolveTarget(s.requiredText(args, "target"));
        int window = args.path("window").asInt(20);
        if (window < 2 || window > 240) throw new MetricException("PARAM_INVALID", "window는 2~240 사이여야 합니다");
        LocalDate asOf = args.hasNonNull("as_of") ? LocalDate.parse(args.get("as_of").asText()) : LocalDate.now();
        if (asOf.isAfter(LocalDate.now())) throw new MetricException("PERIOD_FUTURE", "미래 기준일은 불가: " + asOf);

        // 휴장일 감안해 window의 2배 캘린더 일수 + 14일 여유로 조회
        LocalDate from = asOf.minusDays(window * 2L + 14);
        List<DailyQuote> quotes = s.api().dailyQuotes(info, from, asOf);
        double ma = Calculations.movingAverage(quotes, window);
        double close = quotes.get(quotes.size() - 1).close();
        double disparity = (close / ma - 1) * 100;

        List<DailyQuote> windowQuotes = quotes.subList(Math.max(0, quotes.size() - window), quotes.size());
        return new MetricResult(
            MetricResult.newCardId(), "MA_DISPARITY",
            "%s %d일선 이격도 (기준일 %s)".formatted(info.name(), window, quotes.get(quotes.size() - 1).date()),
            windowQuotes.get(0).date(), quotes.get(quotes.size() - 1).date(),
            List.of(new MetricResult.Target(info.code(), info.name())),
            List.of(new MetricResult.Headline(window + "일선 이격도", ReturnGapExecutor.round4(disparity), "%"),
                    new MetricResult.Headline("현재가", close, "원"),
                    new MetricResult.Headline(window + "일 이동평균", ReturnGapExecutor.round4(ma), "원")),
            new MetricResult.ChartSpec("line", List.of(s.closeSeries(info.name(), windowQuotes))),
            new MetricResult.Evidence(
                List.of(s.apiCall("stock-daily", "일별 시세 조회", info,
                        new ExecutorSupport.Period(from, asOf))),
                List.of(s.rawRows(info.name(), windowQuotes)),
                "이격도(%) = (현재가 / N일 이동평균 − 1) × 100  (양수 = 이평선 위)",
                List.of(
                    new MetricResult.Evidence.Step(window + "일 이동평균",
                        "최근 %d개 종가 평균 = %s".formatted(window, ExecutorSupport.fmt(ma))),
                    new MetricResult.Evidence.Step("이격도",
                        "(%s / %s − 1) × 100 = %s%%".formatted(
                            ExecutorSupport.fmt(close), ExecutorSupport.fmt(ma), ExecutorSupport.fmt(disparity))))));
    }
}
```

`ReturnRankingExecutor.java`:

```java
package com.koscom.kopilot.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.koscom.kopilot.checkapi.DailyQuote;
import com.koscom.kopilot.checkapi.StockInfo;
import com.koscom.kopilot.domain.Calculations;
import com.koscom.kopilot.domain.MetricException;
import com.koscom.kopilot.domain.MetricResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class ReturnRankingExecutor implements MetricExecutor {

    private final ExecutorSupport s;

    public ReturnRankingExecutor(ExecutorSupport s) { this.s = s; }

    @Override public String toolName() { return "return_ranking"; }

    @Override public String description() {
        return "사용자가 나열한 복수 종목(2~10개)의 기간수익률을 계산해 순위를 매긴다. "
             + "예: '에코프로, 엘앤에프, 포스코퓨처엠 3개월 수익률 순위'. "
             + "주의: 테마명·업종명만 있고 종목이 나열되지 않았으면 이 tool을 쓰지 말고 종목을 되물을 것.";
    }

    @Override public Map<String, Object> inputSchemaProperties() {
        return Map.of(
            "targets", Map.of("type", "array", "items", Map.of("type", "string"),
                    "description", "순위를 매길 종목들의 한글 종목명 (2~10개, 사용자가 직접 나열한 것)"),
            "from", Map.of("type", "string", "description", "조회 시작일 YYYY-MM-DD"),
            "to", Map.of("type", "string", "description", "조회 종료일 YYYY-MM-DD"));
    }

    @Override public List<String> requiredParams() { return List.of("targets", "from", "to"); }

    @Override public MetricResult execute(JsonNode args) {
        JsonNode targetsNode = args.path("targets");
        if (!targetsNode.isArray() || targetsNode.size() < 2 || targetsNode.size() > 10) {
            throw new MetricException("PARAM_INVALID", "targets는 2~10개 종목 배열이어야 합니다");
        }
        ExecutorSupport.Period p = s.parsePeriod(args);

        record Entry(StockInfo info, double ret, List<DailyQuote> quotes) {}
        List<Entry> entries = new ArrayList<>();
        for (JsonNode t : targetsNode) {
            StockInfo info = s.resolveTarget(t.asText());
            List<DailyQuote> quotes = s.api().dailyQuotes(info, p.from(), p.to());
            entries.add(new Entry(info, Calculations.periodReturnPct(quotes), quotes));
        }
        entries.sort(Comparator.comparingDouble(Entry::ret).reversed());

        List<MetricResult.Target> targets = new ArrayList<>();
        List<MetricResult.Headline> headline = new ArrayList<>();
        List<MetricResult.ChartSpec.Point> bars = new ArrayList<>();
        List<MetricResult.Evidence.ApiCall> apiCalls = new ArrayList<>();
        List<MetricResult.Evidence.RawSeries> raw = new ArrayList<>();
        List<MetricResult.Evidence.Step> steps = new ArrayList<>();

        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            targets.add(new MetricResult.Target(e.info().code(), e.info().name()));
            headline.add(new MetricResult.Headline(
                    "%d위 %s".formatted(i + 1, e.info().name()), ReturnGapExecutor.round4(e.ret()), "%"));
            bars.add(new MetricResult.ChartSpec.Point(e.info().name(), ReturnGapExecutor.round4(e.ret())));
            apiCalls.add(s.apiCall("stock-daily", "일별 시세 조회", e.info(), p));
            raw.add(s.rawRows(e.info().name(), e.quotes()));
            steps.add(new MetricResult.Evidence.Step("%d위 %s".formatted(i + 1, e.info().name()),
                    "(%s / %s − 1) × 100 = %s%%".formatted(
                        ExecutorSupport.fmt(e.quotes().get(e.quotes().size() - 1).close()),
                        ExecutorSupport.fmt(e.quotes().get(0).close()), ExecutorSupport.fmt(e.ret()))));
        }

        return new MetricResult(
            MetricResult.newCardId(), "RETURN_RANKING",
            "기간수익률 랭킹 (%s ~ %s)".formatted(p.from(), p.to()),
            p.from(), p.to(), targets, headline,
            new MetricResult.ChartSpec("bar",
                List.of(new MetricResult.ChartSpec.Series("기간수익률(%)", bars))),
            new MetricResult.Evidence(apiCalls, raw,
                "기간수익률(%) = (기간 마지막 종가 / 기간 첫 종가 − 1) × 100, 내림차순 정렬",
                steps));
    }
}
```

`CatalogConfig`에 Bean 추가:

```java
    @Bean public NavDisparityExecutor navDisparityExecutor(ExecutorSupport s) { return new NavDisparityExecutor(s); }
    @Bean public MaDisparityExecutor maDisparityExecutor(ExecutorSupport s) { return new MaDisparityExecutor(s); }
    @Bean public ReturnRankingExecutor returnRankingExecutor(ExecutorSupport s) { return new ReturnRankingExecutor(s); }
```

참고: `NavDisparityExecutor`/`MaDisparityExecutor`에서 `Calculations` import가 사용되지 않으면 제거한다(컴파일 경고 방지).

- [ ] **Step 4: 테스트 통과 확인** (전체 실행기 6종 완성 시점 — 회귀 포함 전체 테스트)

```bash
./gradlew test
```

Expected: BUILD SUCCESSFUL, 전체 통과.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat: nav disparity, ma disparity and return ranking executors (catalog complete)"
```
---

### Task 8: 가이드 모듈 (F코드 역인덱스 검색 + 레시피 tool 2종)

CHECK API는 776개이고 응답 필드 슬롯이 24,648개다. 이걸 LLM 컨텍스트에 넣는 건 불가능하고, 넣어도 소용이 없다 — **API 제목이 전부 `[일별정보]`, `[기본정보]`처럼 무의미해서 제목만 보고는 고를 수 없기 때문이다.** 실제 의미는 반환 필드에 있다.

**설계: 검색은 Java가 하고, LLM은 좁혀진 후보로 레시피만 쓴다.**

핵심 근거 두 가지 (2026-07-22 실측 — `docs/check-api/README.md`):

1. **F코드는 전역 사전이다.** 24,648개 필드 슬롯이 고유 코드 1,841개로 수렴하고, `F15001`은 어느 API에서든 현재가다. 의미가 갈리는 코드는 5.7%뿐이며 그마저 `단축코드`/`종목코드` 수준의 표기 차이다. 따라서 API별 필드 설명을 저장하지 않고 **전역 사전 1개 + API별 코드 목록**으로 관리한다(전량 인라인 334KB → 46KB).
2. **`detail` 필드에 진짜 의미가 있다.** `F06508_11`의 `desc`는 `종목투자자별순매수거래량11`이라 그것만으로는 쓸 수 없고, `detail="외국인"`이 있어야 실행 가능한 레시피가 된다. 전체 필드의 43.3%가 `detail`을 갖는다.

이 둘을 합치면 **역인덱스**가 나온다: 질문 키워드 → F코드 설명 검색 → 해당 코드를 반환하는 API. 실측 결과 "외국인 순매수"는 F코드 22개 → API 104개, "공매도"는 16개 → 27개로 걸린다. 104개는 많으므로 랭킹으로 3~5개까지 좁힌 뒤 LLM에 넘긴다.

**어휘 불일치는 LLM이 푼다.** 사용자가 "수급", "외인"이라고 하면 F코드 설명에 그 단어가 없어 0건이 된다. 그래서 `explain_recipe`는 `topic`과 함께 **LLM이 생성한 `keywords` 배열**을 받는다(Claude는 "수급 = 투자자별 매매"를 이미 안다). 백엔드는 여기에 동의어 사전 20~30개를 더해 보정한다.

> **임베딩 RAG를 쓰지 않는 이유**: 코퍼스가 산문이 아니라 1,841개의 짧은 도메인 용어다. 한국어 임베딩 품질이 불안정하고, 검색 실패 원인을 볼 수 없으며, 사용자가 `F15301`처럼 코드를 직접 말할 때 정확 매칭이 오히려 약해진다. 어휘 불일치라는 실제 약점은 LLM 질의 확장 + 동의어 사전으로 결정적이고 디버깅 가능하게 해결된다. 카탈로그가 수만 건이 되거나 산문 문서(가이드북·FAQ)가 들어오면 그때 하이브리드 검색을 로드맵으로 추가한다.

**Files:**
- Create: `backend/src/main/resources/check-api/fcodes.json`, `apis.json` (리포 `docs/check-api/`에서 복사)
- Create: `backend/src/main/resources/check-api/synonyms.yaml`, `api-aliases.yaml`
- Create: `backend/src/main/java/com/koscom/kopilot/guide/FieldDictionary.java`, `ApiSpecEntry.java`, `ApiSpecIndex.java`, `GuideService.java`
- Modify: `backend/src/main/java/com/koscom/kopilot/catalog/ExecutorSupport.java` (SPEC_URLS 하드코딩 → ApiSpecIndex 주입), `CatalogConfig.java`
- Modify: Task 5~7의 실행기 테스트 — `new ExecutorSupport(client, resolver)` 호출부에 `ApiSpecIndex.loadFromClasspath()` 인자를 추가한다(6개 파일). 생성자가 2-arg → 3-arg로 바뀌므로 이 Task에서 함께 고쳐야 컴파일된다
- Test: `backend/src/test/java/com/koscom/kopilot/guide/FieldDictionaryTest.java`, `GuideServiceTest.java`

**Interfaces:**
- Consumes: Task 5 `ExecutorSupport`
- Produces:
  - `record ApiSpecEntry(String apiId, String name, String path, String summary, List<Param> params, String docUrl, List<Field> fields)`
    - `record Param(String name, boolean required)` / `record Field(String code, String label)`
    - `fields`는 **검색에 매칭된 필드만** 담는다(122개 전체가 아니라 외국인 관련 4개). 전체가 필요하면 `get_api_spec`이 채운다
  - `class FieldDictionary { String label(String code); Set<String> search(List<String> keywords); }` — 1,841개 F코드 전역 사전
  - `class ApiSpecIndex { static ApiSpecIndex loadFromClasspath(); List<ApiSpecEntry> all(); Optional<ApiSpecEntry> byId(String apiId); String docUrl(String apiId); }`
  - `class GuideService { GuideResult recipeContext(String topic, List<String> keywords); List<ApiSpecEntry> specs(List<String> apiIds); }`
  - `record GuideResult(String topic, List<ApiSpecEntry> matched, List<CatalogLine> catalog, List<String> usedKeywords)`
    - `matched` = 상위 5개(매칭 필드 포함), `catalog` = 차순위 10개(근접 후보 — 프론트에서 접기로 표시)
  - `ExecutorSupport.specUrl(apiId)`가 `ApiSpecIndex.docUrl(apiId)`를 사용

> **apiId 규약**: 경로에서 파생한다 — `/stock/m001/hist_info` → `stock-m001-hist_info`. 지표 6종이 쓰는 API에는 `api-aliases.yaml`로 읽기 쉬운 별칭을 준다(`stock-daily`, `index-daily`, `stock-investor` 등). `byId`는 별칭과 파생 id를 모두 받는다 — Task 5의 `specUrl("stock-daily")` 호출부가 그대로 동작해야 하기 때문.

- [ ] **Step 1: 리소스 파일 준비**

리포 루트의 조사 산출물을 백엔드 리소스로 복사한다. 이 두 파일이 가이드 모듈의 원천 데이터다.

```bash
mkdir -p check-kopilot/backend/src/main/resources/check-api
cp docs/check-api/fcodes.json     check-kopilot/backend/src/main/resources/check-api/fcodes.json
cp docs/check-api/apis_full.json  check-kopilot/backend/src/main/resources/check-api/apis.json
```

- `fcodes.json` — `{"F15001":"현재가", "F06508_11":"종목투자자별순매수거래량11(외국인)", ...}` 1,841건
- `apis.json` — `{"/stock/m001/hist_info": {"title":"[일별정보]", "params":[["cust_id","O"],...], "res":["F12506","F15001",...]}, ...}` 776건

`synonyms.yaml` (사용자 표현 → 명세 용어. 실사용에서 0건 매칭이 나올 때마다 한 줄씩 추가한다):

```yaml
synonyms:
  수급: [투자자별, 순매수]
  외인: [외국인]
  기관물량: [기관계, 순매수]
  개미: [개인]
  대차: [대차잔고]
  공매: [공매도]
  시총: [시가총액]
  거래대금: [거래대금]
  괴리율: [ETP지표가치, NAV]
  이격도: [이동평균]
  체결강도: [체결강도]
  프로그램매매: [프로그램]
  배당수익률: [배당]
  실적: [매출액, 영업이익, 당기순이익]
```

`api-aliases.yaml`:

```yaml
aliases:
  stock-daily: /stock/m001/hist_info
  kosdaq-daily: /stock/m003/hist_info
  index-daily: /stock/m002/hist_info
  kosdaq-index-daily: /stock/m004/hist_info
  stock-investor: /stock/m001/invest_hist
  stock-code: /stock/m001/code_info
  etf-code: /stock/m001/code_etf_info
```

- [ ] **Step 2: 실패하는 테스트 작성**

`FieldDictionaryTest.java`:

```java
package com.koscom.kopilot.guide;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

class FieldDictionaryTest {

    private final FieldDictionary dict = FieldDictionary.loadFromClasspath();

    @Test
    void loadsGlobalCodeDictionary() {
        assertThat(dict.size()).isGreaterThan(1500);
        assertThat(dict.label("F15001")).isEqualTo("현재가");
        // detail이 병합돼 있어야 실행 가능한 레시피가 나온다
        assertThat(dict.label("F06508_11")).contains("외국인");
    }

    @Test
    void searchesByKeyword() {
        Set<String> codes = dict.search(List.of("외국인", "순매수"));
        assertThat(codes).contains("F06508_11", "F06511_11");
        // 하나만 걸린 코드는 제외 — 두 키워드를 모두 담은 것이 우선
        assertThat(dict.label(codes.iterator().next())).isNotBlank();
    }

    @Test
    void expandsSynonymsSoUserVocabularyStillHits() {
        // "수급"은 F코드 설명에 없는 단어 — 동의어 사전이 없으면 0건이 된다
        assertThat(dict.search(List.of("수급"))).isNotEmpty();
        assertThat(dict.search(List.of("외인"))).isNotEmpty();
    }

    @Test
    void unknownKeywordReturnsEmpty() {
        assertThat(dict.search(List.of("존재하지않는지표명xyz"))).isEmpty();
    }
}
```

`GuideServiceTest.java`:

```java
package com.koscom.kopilot.guide;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class GuideServiceTest {

    private final ApiSpecIndex index = ApiSpecIndex.loadFromClasspath();
    private final GuideService guide = new GuideService(index, FieldDictionary.loadFromClasspath());

    @Test
    void indexLoadsAllApisAndResolvesAliases() {
        assertThat(index.all()).hasSizeGreaterThan(700);
        assertThat(index.byId("stock-daily")).isPresent()
                .get().extracting(ApiSpecEntry::path).isEqualTo("/stock/m001/hist_info");
        assertThat(index.byId("stock-m001-hist_info")).isPresent();   // 파생 id도 받는다
        assertThat(index.docUrl("stock-daily")).startsWith("https://checkapi.koscom.co.kr");
    }

    @Test
    void narrowsInvestorFlowQuestionToStockInvestHist() {
        GuideService.GuideResult r = guide.recipeContext("외국인 순매수 동향", List.of("외국인", "순매수"));

        assertThat(r.matched()).hasSizeLessThanOrEqualTo(5);
        // 104개 후보 중 주식 정본 모듈이 1위여야 한다 (파생 m238 등이 아니라)
        assertThat(r.matched().get(0).path()).isEqualTo("/stock/m001/invest_hist");
        // 매칭된 필드만 담는다 — 122개 전체가 아니라
        assertThat(r.matched().get(0).fields()).hasSizeLessThan(20);
        assertThat(r.matched().get(0).fields()).extracting(ApiSpecEntry.Field::label)
                .anySatisfy(l -> assertThat(l).contains("외국인"));
    }

    @Test
    void prefersCanonicalModuleOverNxtDuplicates() {
        GuideService.GuideResult r = guide.recipeContext("공매도 잔고", List.of("공매도"));
        // m222~m225(NXT/통합)는 m001/m003과 필드가 같으므로 뒤로 밀려야 한다
        assertThat(r.matched()).extracting(ApiSpecEntry::path)
                .noneMatch(p -> p.startsWith("/stock/m22"));
    }

    @Test
    void returnsRunnerUpsAsCatalogForCollapsibleUi() {
        GuideService.GuideResult r = guide.recipeContext("공매도 잔고", List.of("공매도"));
        assertThat(r.catalog()).isNotEmpty().hasSizeLessThanOrEqualTo(10);
        assertThat(r.usedKeywords()).contains("공매도");
    }

    @Test
    void noMatchReturnsEmptyMatchedNotException() {
        GuideService.GuideResult r = guide.recipeContext("존재하지 않는 지표", List.of("존재하지않는지표명xyz"));
        assertThat(r.matched()).isEmpty();   // LLM이 "제공 범위 밖"이라고 안내하도록
    }

    @Test
    void specsReturnsFullFieldListForRequestedApis() {
        List<ApiSpecEntry> specs = guide.specs(List.of("stock-investor"));
        assertThat(specs).hasSize(1);
        // get_api_spec은 전체 필드를 채운다
        assertThat(specs.get(0).fields()).hasSizeGreaterThan(100);
    }
}
```

- [ ] **Step 3: 테스트 실패 확인**

```bash
./gradlew test --tests '*FieldDictionaryTest' --tests '*GuideServiceTest'
```

Expected: 컴파일 에러로 FAIL.

- [ ] **Step 4: 구현**

`FieldDictionary.java`:

```java
package com.koscom.kopilot.guide;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.yaml.snakeyaml.Yaml;

import java.util.*;

/**
 * F코드 전역 사전. CHECK API의 F코드는 API와 무관하게 의미가 동일하므로(2026-07-22 실측: 24,648 슬롯 → 1,841 코드)
 * API별로 필드 설명을 중복 저장하지 않고 여기 하나만 둔다.
 * label에는 명세의 desc와 detail이 병합돼 있다 — detail 없이는 "순매수거래량11"이 외국인인지 알 수 없다.
 */
public class FieldDictionary {

    private final Map<String, String> labels;
    private final Map<String, List<String>> synonyms;

    public FieldDictionary(Map<String, String> labels, Map<String, List<String>> synonyms) {
        this.labels = labels;
        this.synonyms = synonyms;
    }

    @SuppressWarnings("unchecked")
    public static FieldDictionary loadFromClasspath() {
        try (var codes = new ClassPathResource("check-api/fcodes.json").getInputStream();
             var syn = new ClassPathResource("check-api/synonyms.yaml").getInputStream()) {
            Map<String, String> labels = new ObjectMapper()
                    .readValue(codes, new TypeReference<Map<String, String>>() {});
            Map<String, Object> root = new Yaml().load(syn);
            Map<String, List<String>> synonyms =
                    (Map<String, List<String>>) root.getOrDefault("synonyms", Map.of());
            return new FieldDictionary(labels, synonyms);
        } catch (Exception e) {
            throw new IllegalStateException("F코드 사전 로드 실패", e);
        }
    }

    public int size() { return labels.size(); }

    public String label(String code) { return labels.getOrDefault(code, code); }

    /** 사용자 표현을 명세 용어로 확장한다. LLM이 넘긴 keywords의 어휘 불일치를 보정하는 안전망. */
    public List<String> expand(List<String> keywords) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String k : keywords) {
            String t = k == null ? "" : k.trim();
            if (t.isEmpty()) continue;
            out.add(t);
            out.addAll(synonyms.getOrDefault(t, List.of()));
        }
        return new ArrayList<>(out);
    }

    /**
     * 키워드를 모두 포함하는 코드를 우선하고, 없으면 하나라도 포함하는 코드를 반환한다.
     * (AND 우선 → OR 폴백. "외국인 순매수"가 "외국인" 전부를 끌고 오는 것을 막는다)
     */
    public Set<String> search(List<String> keywords) {
        List<String> terms = expand(keywords);
        if (terms.isEmpty()) return Set.of();

        Set<String> all = new LinkedHashSet<>();
        Set<String> any = new LinkedHashSet<>();
        for (var e : labels.entrySet()) {
            String label = e.getValue();
            int hits = 0;
            for (String t : terms) if (label.contains(t)) hits++;
            if (hits == 0) continue;
            any.add(e.getKey());
            if (hits >= Math.min(terms.size(), 2)) all.add(e.getKey());
        }
        return all.isEmpty() ? any : all;
    }
}
```

`ApiSpecEntry.java`:

```java
package com.koscom.kopilot.guide;

import java.util.List;

public record ApiSpecEntry(String apiId, String name, String path, String summary,
                           List<Param> params, String docUrl, List<Field> fields) {

    public record Param(String name, boolean required) {}
    public record Field(String code, String label) {}
}
```

`ApiSpecIndex.java`:

```java
package com.koscom.kopilot.guide;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.yaml.snakeyaml.Yaml;

import java.util.*;

/** CHECK API 776건의 경로·파라미터·반환 F코드 목록. 필드 설명은 FieldDictionary가 갖는다. */
public class ApiSpecIndex {

    /** 원본 레코드 — 반환 필드는 코드 목록만 보관한다 */
    public record Raw(String path, String title, List<String> codes, List<ApiSpecEntry.Param> params) {}

    private final Map<String, Raw> byPath;      // path -> raw
    private final Map<String, String> idToPath; // apiId(별칭 + 파생 id) -> path

    public ApiSpecIndex(Map<String, Raw> byPath, Map<String, String> idToPath) {
        this.byPath = byPath;
        this.idToPath = idToPath;
    }

    /** `/stock/m001/hist_info` → `stock-m001-hist_info` */
    public static String derivedId(String path) {
        return path.replaceFirst("^/", "").replace('/', '-');
    }

    /** 문서 페이지 URL. 개별 페이지 매핑이 없으므로 카테고리 목록 페이지로 보낸다(스펙 13절 "명세 링크 부재" 대응). */
    public static String docUrlOf(String path) {
        String category = path.replaceFirst("^/", "").split("/")[0];
        return "https://checkapi.koscom.co.kr/#/" + category;
    }

    @SuppressWarnings("unchecked")
    public static ApiSpecIndex loadFromClasspath() {
        try (var apisIn = new ClassPathResource("check-api/apis.json").getInputStream();
             var aliasIn = new ClassPathResource("check-api/api-aliases.yaml").getInputStream()) {

            Map<String, Map<String, Object>> raw = new ObjectMapper()
                    .readValue(apisIn, new TypeReference<Map<String, Map<String, Object>>>() {});

            Map<String, Raw> byPath = new LinkedHashMap<>();
            Map<String, String> idToPath = new LinkedHashMap<>();
            for (var e : raw.entrySet()) {
                String path = e.getKey();
                List<String> codes = (List<String>) e.getValue().getOrDefault("res", List.of());
                List<List<String>> ps = (List<List<String>>) e.getValue().getOrDefault("params", List.of());
                List<ApiSpecEntry.Param> params = ps.stream()
                        .map(p -> new ApiSpecEntry.Param(p.get(0), "O".equals(p.get(1)))).toList();
                byPath.put(path, new Raw(path, String.valueOf(e.getValue().getOrDefault("title", "")), codes, params));
                idToPath.put(derivedId(path), path);
            }

            Map<String, Object> aliasRoot = new Yaml().load(aliasIn);
            Map<String, String> aliases =
                    (Map<String, String>) aliasRoot.getOrDefault("aliases", Map.of());
            aliases.forEach((alias, path) -> {
                if (!byPath.containsKey(path)) {
                    throw new IllegalStateException("api-aliases.yaml의 경로가 apis.json에 없다: " + path);
                }
                idToPath.put(alias, path);
            });

            return new ApiSpecIndex(byPath, idToPath);
        } catch (Exception e) {
            throw new IllegalStateException("API 인덱스 로드 실패", e);
        }
    }

    public Collection<Raw> raws() { return byPath.values(); }

    public List<ApiSpecEntry> all() {
        return byPath.values().stream().map(r -> toEntry(r, List.of(), null)).toList();
    }

    public Optional<ApiSpecEntry> byId(String apiId) {
        String path = idToPath.get(apiId);
        if (path == null) path = byPath.containsKey(apiId) ? apiId : null;   // 경로 자체도 허용
        return Optional.ofNullable(path).map(byPath::get).map(r -> toEntry(r, List.of(), null));
    }

    public Optional<Raw> rawById(String apiId) {
        String path = idToPath.getOrDefault(apiId, byPath.containsKey(apiId) ? apiId : null);
        return Optional.ofNullable(path).map(byPath::get);
    }

    public String docUrl(String apiId) {
        return rawById(apiId).map(r -> docUrlOf(r.path())).orElse("");
    }

    /** 별칭이 있으면 별칭을 apiId로 노출한다(레시피 가독성) */
    private String preferredId(String path) {
        return idToPath.entrySet().stream()
                .filter(e -> e.getValue().equals(path) && !e.getKey().equals(derivedId(path)))
                .map(Map.Entry::getKey).findFirst().orElse(derivedId(path));
    }

    ApiSpecEntry toEntry(Raw r, List<String> showCodes, FieldDictionary dict) {
        List<ApiSpecEntry.Field> fields = showCodes.stream()
                .map(c -> new ApiSpecEntry.Field(c, dict == null ? c : dict.label(c))).toList();
        return new ApiSpecEntry(preferredId(r.path()), r.title(), r.path(),
                r.title() + " — 반환 필드 " + r.codes().size() + "개",
                r.params(), docUrlOf(r.path()), fields);
    }
}
```

`GuideService.java`:

```java
package com.koscom.kopilot.guide;

import java.util.*;

/**
 * 가이드(레시피) 모드의 검색 담당.
 * LLM에게 776개 API를 보여주지 않는다 — 여기서 3~5개로 좁혀 넘기고, LLM은 레시피 문장만 쓴다.
 */
public class GuideService {

    private static final int TOP_N = 5;
    private static final int RUNNER_UP_N = 10;

    public record CatalogLine(String apiId, String name, String summary) {}
    public record GuideResult(String topic, List<ApiSpecEntry> matched,
                              List<CatalogLine> catalog, List<String> usedKeywords) {}

    private final ApiSpecIndex index;
    private final FieldDictionary dict;

    public GuideService(ApiSpecIndex index, FieldDictionary dict) {
        this.index = index;
        this.dict = dict;
    }

    public GuideResult recipeContext(String topic, List<String> keywords) {
        List<String> terms = dict.expand(keywords == null ? List.of() : keywords);
        Set<String> codes = dict.search(terms);
        if (codes.isEmpty()) return new GuideResult(topic, List.of(), List.of(), terms);

        record Scored(ApiSpecIndex.Raw raw, List<String> hits, int score) {}
        List<Scored> scored = new ArrayList<>();
        for (ApiSpecIndex.Raw r : index.raws()) {
            List<String> hits = r.codes().stream().filter(codes::contains).toList();
            if (hits.isEmpty()) continue;
            scored.add(new Scored(r, hits, score(r, hits.size(), topic)));
        }
        scored.sort(Comparator.comparingInt(Scored::score).reversed());

        List<ApiSpecEntry> matched = scored.stream().limit(TOP_N)
                .map(s -> index.toEntry(s.raw(), s.hits(), dict)).toList();
        List<CatalogLine> runnerUps = scored.stream().skip(TOP_N).limit(RUNNER_UP_N)
                .map(s -> index.toEntry(s.raw(), List.of(), dict))
                .map(e -> new CatalogLine(e.apiId(), e.name(), e.summary())).toList();
        return new GuideResult(topic, matched, runnerUps, terms);
    }

    /** get_api_spec — 지정한 API의 전체 필드를 채워 반환한다 */
    public List<ApiSpecEntry> specs(List<String> apiIds) {
        return apiIds.stream()
                .map(index::rawById)
                .flatMap(Optional::stream)
                .map(r -> index.toEntry(r, r.codes(), dict))
                .toList();
    }

    /**
     * 랭킹. 같은 필드 패턴이 여러 모듈에 중복되므로(실측: "외국인 순매수" 104개 매칭) 정본을 앞세운다.
     *  - 주식 질문인데 파생/채권 모듈이 1위로 오는 것을 막는다
     *  - NXT/통합(m222~m225)은 거래소 정본(m001/m003)과 필드가 같으므로 뒤로 민다
     *  - 기간 질문이면 hist_info를, 현재 스냅샷 질문이면 basic_info를 우선
     */
    private int score(ApiSpecIndex.Raw r, int hitCount, String topic) {
        String p = r.path();
        int s = hitCount * 10;

        if (p.startsWith("/stock/")) s += 40;
        else if (p.startsWith("/etc/")) s += 10;
        else s -= 20;                                   // future / bond / ext

        if (p.matches("^/stock/m(001|002|003|004)/.*")) s += 25;   // 거래소·코스닥 정본
        if (p.matches("^/stock/m22[2-5]/.*")) s -= 30;             // NXT/통합 중복
        if (p.endsWith("_port")) s -= 15;                          // 복수종목 변형은 기본형 뒤로

        boolean periodQuestion = topic != null && topic.matches(".*(기간|일별|추이|동향|최근|이력|변화).*");
        if (p.contains("hist")) s += periodQuestion ? 20 : 5;
        if (p.contains("basic")) s += periodQuestion ? 0 : 10;
        if (p.contains("intra") || p.contains("tick") || p.contains("hoga")) s -= 10;

        return s;
    }
}
```

`ExecutorSupport` 수정 — 하드코딩 `SPEC_URLS` 제거, 생성자에 `ApiSpecIndex` 추가:

```java
    private final ApiSpecIndex specIndex;

    public ExecutorSupport(CheckApiClient client, StockResolver resolver, ApiSpecIndex specIndex) {
        this.client = client;
        this.resolver = resolver;
        this.specIndex = specIndex;
    }

    public String specUrl(String apiId) { return specIndex.docUrl(apiId); }
```

`CatalogConfig` — `ApiSpecIndex`·`FieldDictionary`·`GuideService` 빈 등록:

```java
    @Bean
    public FieldDictionary fieldDictionary() { return FieldDictionary.loadFromClasspath(); }

    @Bean
    public ApiSpecIndex apiSpecIndex() { return ApiSpecIndex.loadFromClasspath(); }

    @Bean
    public GuideService guideService(ApiSpecIndex index, FieldDictionary dict) {
        return new GuideService(index, dict);
    }
```

- [ ] **Step 5: 테스트 통과 확인**

```bash
./gradlew test           # ExecutorSupport 생성자 변경 때문에 전체를 돌린다
```

Expected: BUILD SUCCESSFUL. Task 5~7 테스트가 컴파일 에러를 내면 `new ExecutorSupport(...)` 호출부에 `ApiSpecIndex.loadFromClasspath()`를 추가한다.

랭킹이 기대와 다르면 `score()`의 가중치를 조정한다. 조정 근거를 남기기 위해, 실패한 질문은 `GuideServiceTest`에 케이스로 추가한 뒤 고친다.

- [ ] **Step 6: 검색 품질 수동 점검** (랭킹 가중치 튜닝용 — 발표 데모 질문 기준)

```bash
./gradlew test --tests '*GuideServiceTest' --info | grep -A5 '외국인'
```

아래 질문들이 의도한 API를 1위로 뽑는지 확인하고, 아니면 `score()`를 조정한다:

| 질문 | 기대 1위 |
|---|---|
| 삼성전자 외국인 순매수 동향 | `/stock/m001/invest_hist` |
| 공매도 잔고 추이 | `/stock/m001/short_hist_info` |
| 신용잔고 얼마나 쌓였어 | `/stock/m001/credit_hist_info` |
| 대차잔고 보여줘 | `/stock/m001/loan_hist_info` |
| 프로그램매매 동향 | `/stock/m001/program` 계열 |

0건이 나오는 표현이 있으면 `synonyms.yaml`에 한 줄 추가한다. **이게 이 모듈의 주된 튜닝 수단이다** — 임베딩 재학습이 아니라.

- [ ] **Step 7: Commit**

```bash
git add -A && git commit -m "feat: guide module with global f-code dictionary and reverse-index api search"
```
---

### Task 9: 카드 저장소 + xlsx 내보내기

**Files:**
- Create: `backend/src/main/java/com/koscom/kopilot/export/CardStore.java`, `XlsxExportService.java`, `ExportController.java`
- Test: `backend/src/test/java/com/koscom/kopilot/export/XlsxExportServiceTest.java`

**Interfaces:**
- Consumes: Task 1 `card` 테이블, Task 4 `MetricResult`
- Produces:
  - `class CardStore { void save(String sessionId, MetricResult r); Optional<MetricResult> find(String cardId); }`
  - `class XlsxExportService { byte[] toXlsx(MetricResult r); }` — 시트1 `결과 요약`, 시트2 `원본 데이터`, 시트3 `계산 과정`
  - HTTP: `GET /api/cards/{cardId}/xlsx` → `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`, 파일명 `kopilot-{cardId앞8자}.xlsx` (없으면 404)

- [ ] **Step 1: 실패하는 테스트 작성** — `XlsxExportServiceTest.java` (스냅샷 검증: xlsx 수치 == 카드 수치, 스펙 11절 3항)

```java
package com.koscom.kopilot.export;

import com.koscom.kopilot.domain.MetricResult;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class XlsxExportServiceTest {

    private final XlsxExportService service = new XlsxExportService();

    private MetricResult sampleCard() {
        return new MetricResult(
            "test-card-id", "RETURN_GAP", "삼성전자 vs 코스피 수익률 갭",
            LocalDate.parse("2026-07-13"), LocalDate.parse("2026-07-17"),
            List.of(new MetricResult.Target("005930", "삼성전자"),
                    new MetricResult.Target("KOSPI", "코스피")),
            List.of(new MetricResult.Headline("수익률 갭", 3.0, "%p")),
            new MetricResult.ChartSpec("line", List.of()),
            new MetricResult.Evidence(
                List.of(new MetricResult.Evidence.ApiCall("일별 시세 조회", "삼성전자(005930) 2026-07-13 ~ 2026-07-17", "https://example")),
                List.of(new MetricResult.Evidence.RawSeries("삼성전자", List.of(
                        new MetricResult.Evidence.Row(LocalDate.parse("2026-07-13"), 100.0),
                        new MetricResult.Evidence.Row(LocalDate.parse("2026-07-17"), 105.0)))),
                "수익률 갭 = A − B",
                List.of(new MetricResult.Evidence.Step("수익률 갭", "5.0 − 2.0 = 3.0%p"))));
    }

    @Test
    void producesThreeSheets_andSummaryValuesMatchCard() throws Exception {
        byte[] bytes = service.toXlsx(sampleCard());
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertThat(wb.getNumberOfSheets()).isEqualTo(3);
            assertThat(wb.getSheetName(0)).isEqualTo("결과 요약");
            assertThat(wb.getSheetName(1)).isEqualTo("원본 데이터");
            assertThat(wb.getSheetName(2)).isEqualTo("계산 과정");

            var summary = wb.getSheetAt(0);
            // 헤드라인 행: [label, value, unit] — 카드 수치와 xlsx 수치 일치(스냅샷)
            var headlineRow = summary.getRow(4);
            assertThat(headlineRow.getCell(0).getStringCellValue()).isEqualTo("수익률 갭");
            assertThat(headlineRow.getCell(1).getNumericCellValue()).isEqualTo(3.0);

            var raw = wb.getSheetAt(1);
            assertThat(raw.getRow(1).getCell(2).getNumericCellValue()).isEqualTo(100.0);

            var calc = wb.getSheetAt(2);
            assertThat(calc.getRow(0).getCell(1).getStringCellValue()).isEqualTo("수익률 갭 = A − B");
        }
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

```bash
./gradlew test --tests '*XlsxExportServiceTest'
```

Expected: 컴파일 에러로 FAIL.

- [ ] **Step 3: 구현**

`XlsxExportService.java`:

```java
package com.koscom.kopilot.export;

import com.koscom.kopilot.domain.MetricResult;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;

@Service
public class XlsxExportService {

    public byte[] toXlsx(MetricResult r) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            buildSummary(wb.createSheet("결과 요약"), r);
            buildRawData(wb.createSheet("원본 데이터"), r);
            buildCalcSteps(wb.createSheet("계산 과정"), r);
            wb.write(out);
            return out.toByteArray();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("xlsx 생성 실패", e);
        }
    }

    private void buildSummary(Sheet sheet, MetricResult r) {
        int i = 0;
        row(sheet, i++, "제목", r.title());
        row(sheet, i++, "지표", r.metric());
        row(sheet, i++, "기간", r.from() + " ~ " + r.to());
        row(sheet, i++, "대상", r.targets().stream()
                .map(t -> t.name() + "(" + t.code() + ")").reduce((a, b) -> a + ", " + b).orElse(""));
        for (MetricResult.Headline h : r.headline()) {          // 4행부터 헤드라인
            Row row = sheet.createRow(i++);
            row.createCell(0).setCellValue(h.label());
            row.createCell(1).setCellValue(h.value());          // 숫자 셀 — 카드 수치 그대로
            row.createCell(2).setCellValue(h.unit());
        }
        int apiStart = i + 1;
        sheet.createRow(apiStart - 1).createCell(0).setCellValue("호출 API");
        for (MetricResult.Evidence.ApiCall c : r.evidence().apiCalls()) {
            Row row = sheet.createRow(apiStart++);
            row.createCell(0).setCellValue(c.api());
            row.createCell(1).setCellValue(c.request());
            row.createCell(2).setCellValue(c.specUrl());
        }
    }

    private void buildRawData(Sheet sheet, MetricResult r) {
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("시리즈");
        header.createCell(1).setCellValue("일자");
        header.createCell(2).setCellValue("값");
        int i = 1;
        for (MetricResult.Evidence.RawSeries s : r.evidence().rawData()) {
            for (MetricResult.Evidence.Row dataRow : s.rows()) {
                Row row = sheet.createRow(i++);
                row.createCell(0).setCellValue(s.name());
                row.createCell(1).setCellValue(dataRow.date().toString());
                row.createCell(2).setCellValue(dataRow.value());
            }
        }
    }

    private void buildCalcSteps(Sheet sheet, MetricResult r) {
        row(sheet, 0, "공식", r.evidence().formula());
        int i = 2;
        for (MetricResult.Evidence.Step s : r.evidence().steps()) {
            Row row = sheet.createRow(i++);
            row.createCell(0).setCellValue(s.label());
            row.createCell(1).setCellValue(s.detail());
        }
    }

    private void row(Sheet sheet, int idx, String k, String v) {
        Row row = sheet.createRow(idx);
        row.createCell(0).setCellValue(k);
        row.createCell(1).setCellValue(v);
    }
}
```

`CardStore.java`:

```java
package com.koscom.kopilot.export;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.koscom.kopilot.domain.MetricResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class CardStore {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    public CardStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void save(String sessionId, MetricResult r) {
        try {
            // MySQL card.id는 CHAR(36) — UUID 형식만 허용하고 문자열로 바인딩한다
            jdbc.update("INSERT INTO card(id, session_id, payload) VALUES (?,?,?)",
                    UUID.fromString(r.cardId()).toString(), sessionId, mapper.writeValueAsString(r));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("카드 직렬화 실패", e);
        }
    }

    public Optional<MetricResult> find(String cardId) {
        String id;
        try { id = UUID.fromString(cardId).toString(); }
        catch (IllegalArgumentException e) { return Optional.empty(); }   // 잘못된 cardId → 404
        var list = jdbc.queryForList("SELECT payload FROM card WHERE id = ?", String.class, id);
        return list.stream().findFirst().map(json -> {
            try { return mapper.readValue(json, MetricResult.class); }
            catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                throw new IllegalStateException("카드 역직렬화 실패", e);
            }
        });
    }

    /** 직렬화에 쓰는 mapper — ChatService의 카드 SSE 이벤트에도 동일 mapper 사용 */
    public ObjectMapper mapper() { return mapper; }
}
```

`ExportController.java`:

```java
package com.koscom.kopilot.export;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ExportController {

    private final CardStore cardStore;
    private final XlsxExportService xlsx;

    public ExportController(CardStore cardStore, XlsxExportService xlsx) {
        this.cardStore = cardStore;
        this.xlsx = xlsx;
    }

    @GetMapping("/api/cards/{cardId}/xlsx")
    public ResponseEntity<byte[]> download(@PathVariable String cardId) {
        return cardStore.find(cardId)
                .map(card -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                        .header(HttpHeaders.CONTENT_DISPOSITION,
                                "attachment; filename=kopilot-" + cardId.substring(0, 8) + ".xlsx")
                        .body(xlsx.toXlsx(card)))
                .orElse(ResponseEntity.notFound().build());
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

```bash
./gradlew test --tests '*XlsxExportServiceTest'
```

Expected: BUILD SUCCESSFUL. (요약 시트의 헤드라인 시작 행 인덱스가 테스트의 `getRow(4)`와 어긋나면 구현이 아닌 테스트의 행 인덱스를 실제 레이아웃에 맞춰 수정하지 말고, 구현의 레이아웃을 테스트에 맞출 것 — 레이아웃은 계약이다.)

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat: card store and 3-sheet xlsx export with snapshot test"
```
---

### Task 10: Tool 디스패처 (tool 호출 → 실행기/가이드 라우팅 + SSE 이벤트 생성)

Claude 호출부와 분리된, **단위 테스트 가능한** 디스패치 계층. tool 이름과 인자(JsonNode)를 받아 (a) Claude에 돌려줄 tool_result JSON과 (b) 프론트로 밀어낼 SSE 이벤트를 만든다.

**Files:**
- Create: `backend/src/main/java/com/koscom/kopilot/chat/ToolDispatcher.java`, `DispatchResult.java`
- Modify: `backend/src/main/java/com/koscom/kopilot/export/CardStore.java` (`CardSink` 인터페이스 구현 추가)
- Create: `backend/src/main/java/com/koscom/kopilot/export/CardSink.java`
- Test: `backend/src/test/java/com/koscom/kopilot/chat/ToolDispatcherTest.java`

**Interfaces:**
- Consumes: Task 5~8 (`CatalogService`, `GuideService`), Task 9 카드 저장
- Produces:
  - `interface CardSink { void save(String sessionId, MetricResult r); }` (`CardStore implements CardSink`)
  - `record DispatchResult(String toolResultJson, boolean isError, SsePush push)` / `record SsePush(String event, String dataJson)` (push는 null 가능)
  - `class ToolDispatcher { DispatchResult dispatch(String sessionId, String toolName, JsonNode args); }`
  - SSE 이벤트 계약(프론트가 소비): `card`(MetricResult 전문), `clarify`(`{"query","candidates":[{"code","name","market"}]}`), `guide`(`{"topic","matched":[ApiSpecEntry...],"catalog":[{"apiId","name","summary"}...]}`)
  - tool_result 계약(Claude가 소비): 지표 성공 `{"status":"ok","cardId","title","period","headline":[...]}` (원본 시계열 제외 — 토큰 절약), 모호 `{"status":"ambiguous","query","candidates":[...]}`, 미발견 `{"status":"not_found",...}`, 검증실패 `{"status":"error","code","message"}` + `isError=true`

- [ ] **Step 1: 실패하는 테스트 작성** — `ToolDispatcherTest.java`

```java
package com.koscom.kopilot.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.koscom.kopilot.catalog.*;
import com.koscom.kopilot.checkapi.FixtureCheckApiClient;
import com.koscom.kopilot.domain.MetricResult;
import com.koscom.kopilot.export.CardSink;
import com.koscom.kopilot.guide.ApiSpecIndex;
import com.koscom.kopilot.guide.GuideService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class ToolDispatcherTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final List<MetricResult> savedCards = new ArrayList<>();

    private ToolDispatcher dispatcher() {
        ApiSpecIndex index = ApiSpecIndex.loadFromClasspath();
        ExecutorSupport support = new ExecutorSupport(new FixtureCheckApiClient(), TestStocks.resolver(), index);
        CatalogService catalog = new CatalogService(List.of(
                new ReturnGapExecutor(support), new VolatilityExecutor(support),
                new NavDisparityExecutor(support), new MaDisparityExecutor(support),
                new ReturnRankingExecutor(support), new PeriodSummaryExecutor(support)));
        CardSink sink = (sessionId, r) -> savedCards.add(r);
        return new ToolDispatcher(catalog,
                new GuideService(index, FieldDictionary.loadFromClasspath()), sink);
    }

    @Test
    void metricTool_savesCard_emitsCardEvent_returnsCompactResult() throws Exception {
        var r = dispatcher().dispatch("sess-1", "return_gap", mapper.readTree("""
            {"target_a":"삼성전자","target_b":"코스피","from":"2026-07-13","to":"2026-07-17"}"""));

        assertThat(r.isError()).isFalse();
        assertThat(savedCards).hasSize(1);
        assertThat(r.push().event()).isEqualTo("card");
        // SSE에는 근거 포함 전문, tool_result에는 컴팩트 요약(rawData 미포함)
        assertThat(r.push().dataJson()).contains("evidence");
        assertThat(r.toolResultJson()).contains("\"status\":\"ok\"").contains("cardId")
                .doesNotContain("rawData");
    }

    @Test
    void ambiguousStock_emitsClarifyEvent() throws Exception {
        var r = dispatcher().dispatch("sess-1", "period_summary", mapper.readTree("""
            {"target":"에코","from":"2026-07-14","to":"2026-07-17"}"""));

        assertThat(r.isError()).isFalse();      // 에러가 아니라 되묻기 유도
        assertThat(r.push().event()).isEqualTo("clarify");
        assertThat(r.toolResultJson()).contains("\"status\":\"ambiguous\"").contains("에코프로비엠");
    }

    @Test
    void validationFailure_returnsStructuredErrorWithIsError() throws Exception {
        var r = dispatcher().dispatch("sess-1", "return_gap", mapper.readTree("""
            {"target_a":"삼성전자","target_b":"코스피","from":"2026-07-17","to":"2026-07-13"}"""));

        assertThat(r.isError()).isTrue();
        assertThat(r.toolResultJson()).contains("PERIOD_INVERTED");
        assertThat(r.push()).isNull();
    }

    @Test
    void explainRecipe_emitsGuideEvent_withCatalogAndMatches() throws Exception {
        var r = dispatcher().dispatch("sess-1", "explain_recipe", mapper.readTree("""
            {"topic":"외국인 순매수 수급"}"""));

        assertThat(r.isError()).isFalse();
        assertThat(r.push().event()).isEqualTo("guide");
        assertThat(r.toolResultJson()).contains("stock-investor").contains("catalog");
    }

    @Test
    void getApiSpec_returnsFullEntries_noEvent() throws Exception {
        var r = dispatcher().dispatch("sess-1", "get_api_spec", mapper.readTree("""
            {"apiIds":["stock-daily","etf-nav"]}"""));

        assertThat(r.isError()).isFalse();
        assertThat(r.push()).isNull();
        assertThat(r.toolResultJson()).contains("stock-daily").contains("etf-nav");
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

```bash
./gradlew test --tests '*ToolDispatcherTest'
```

Expected: 컴파일 에러로 FAIL.

- [ ] **Step 3: 구현**

`CardSink.java`:

```java
package com.koscom.kopilot.export;

import com.koscom.kopilot.domain.MetricResult;

public interface CardSink {
    void save(String sessionId, MetricResult r);
}
```

(`CardStore`에 `implements CardSink` 추가 — 시그니처 동일하므로 코드 변경 없음)

`DispatchResult.java`:

```java
package com.koscom.kopilot.chat;

public record DispatchResult(String toolResultJson, boolean isError, SsePush push) {
    public record SsePush(String event, String dataJson) {}
}
```

`ToolDispatcher.java`:

```java
package com.koscom.kopilot.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.koscom.kopilot.catalog.CatalogService;
import com.koscom.kopilot.checkapi.AmbiguousStockException;
import com.koscom.kopilot.checkapi.CheckApiException;
import com.koscom.kopilot.checkapi.StockInfo;
import com.koscom.kopilot.checkapi.StockNotFoundException;
import com.koscom.kopilot.domain.MetricException;
import com.koscom.kopilot.domain.MetricResult;
import com.koscom.kopilot.export.CardSink;
import com.koscom.kopilot.guide.GuideService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ToolDispatcher {

    public static final String EXPLAIN_RECIPE = "explain_recipe";
    public static final String GET_API_SPEC = "get_api_spec";

    private final CatalogService catalog;
    private final GuideService guide;
    private final CardSink cards;
    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    public ToolDispatcher(CatalogService catalog, GuideService guide, CardSink cards) {
        this.catalog = catalog;
        this.guide = guide;
        this.cards = cards;
    }

    public DispatchResult dispatch(String sessionId, String toolName, JsonNode args) {
        try {
            if (EXPLAIN_RECIPE.equals(toolName)) return explainRecipe(args);
            if (GET_API_SPEC.equals(toolName)) return getApiSpec(args);
            return metric(sessionId, toolName, args);
        } catch (AmbiguousStockException e) {
            ObjectNode result = mapper.createObjectNode()
                    .put("status", "ambiguous").put("query", e.query());
            result.set("candidates", candidatesNode(e.candidates()));
            ObjectNode event = mapper.createObjectNode().put("query", e.query());
            event.set("candidates", candidatesNode(e.candidates()));
            return new DispatchResult(result.toString(), false,
                    new DispatchResult.SsePush("clarify", event.toString()));
        } catch (StockNotFoundException e) {
            ObjectNode result = mapper.createObjectNode()
                    .put("status", "not_found").put("query", e.query());
            result.set("suggestions", candidatesNode(e.suggestions()));
            return new DispatchResult(result.toString(), false, null);
        } catch (MetricException e) {
            String json = mapper.createObjectNode().put("status", "error")
                    .put("code", e.code()).put("message", e.getMessage()).toString();
            return new DispatchResult(json, true, null);
        } catch (CheckApiException e) {
            String json = mapper.createObjectNode().put("status", "error")
                    .put("code", "CHECK_API_ERROR").put("message", e.getMessage()).toString();
            return new DispatchResult(json, true, null);
        }
    }

    private DispatchResult metric(String sessionId, String toolName, JsonNode args) {
        MetricResult card = catalog.byName(toolName).execute(args);
        cards.save(sessionId, card);
        try {
            String cardJson = mapper.writeValueAsString(card);
            ObjectNode compact = mapper.createObjectNode()
                    .put("status", "ok")
                    .put("cardId", card.cardId())
                    .put("title", card.title())
                    .put("period", card.from() + " ~ " + card.to());
            ArrayNode headline = compact.putArray("headline");
            card.headline().forEach(h -> headline.addObject()
                    .put("label", h.label()).put("value", h.value()).put("unit", h.unit()));
            return new DispatchResult(compact.toString(), false,
                    new DispatchResult.SsePush("card", cardJson));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("카드 직렬화 실패", e);
        }
    }

    private DispatchResult explainRecipe(JsonNode args) {
        String topic = args.path("topic").asText("");
        // LLM이 사용자 표현을 명세 용어로 확장해 넘긴 검색어 (예: "수급" → ["투자자별","순매수"])
        List<String> keywords = new ArrayList<>();
        args.path("keywords").forEach(n -> keywords.add(n.asText()));
        if (keywords.isEmpty() && !topic.isBlank()) keywords.add(topic);
        GuideService.GuideResult r = guide.recipeContext(topic, keywords);
        ObjectNode payload = mapper.createObjectNode().put("topic", topic);
        payload.set("matched", mapper.valueToTree(r.matched()));
        payload.set("catalog", mapper.valueToTree(r.catalog()));
        payload.set("usedKeywords", mapper.valueToTree(r.usedKeywords()));
        String json = payload.toString();
        return new DispatchResult(json, false, new DispatchResult.SsePush("guide", json));
    }

    private DispatchResult getApiSpec(JsonNode args) {
        List<String> ids = new ArrayList<>();
        args.path("apiIds").forEach(n -> ids.add(n.asText()));
        return new DispatchResult(mapper.valueToTree(guide.specs(ids)).toString(), false, null);
    }

    private ArrayNode candidatesNode(List<StockInfo> stocks) {
        ArrayNode arr = mapper.createArrayNode();
        stocks.forEach(s -> arr.addObject()
                .put("code", s.code()).put("name", s.name()).put("market", s.market()));
        return arr;
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

```bash
./gradlew test --tests '*ToolDispatcherTest'
```

Expected: BUILD SUCCESSFUL, 5 tests passed.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat: tool dispatcher routing metric, clarify, guide and spec lookups"
```
---

### Task 11: 수요조사 적재 (가이드 카드 발생 + 추가 요청 버튼)

**설계 판단 — 버튼 클릭 없이 "가이드 카드 발생 자체"를 수요 데이터로 적재한다.**

근거 ① 우리가 알고 싶은 것은 "카탈로그가 못 답한 질문의 분포"이고, 그 신호는 버튼이 아니라 **가이드 카드 트리거 시점**에 이미 100% 발생한다. ② 3분 데모·1~2주 사용 규모에서 버튼 클릭률은 0에 수렴해 표본이 비어버린다(발표에서 보여줄 데이터가 없음). ③ 스펙 6절 "버튼은 기록만"은 버튼의 역할을 제한한 것이지 자동 수집을 금지한 것이 아니다.

따라서 **AUTO(가이드 발생) + EXPLICIT(버튼 클릭) 2종을 같은 테이블에 `source`로 구분해 적재**하고, Admin에서 "수요 건수(AUTO)"와 "강한 수요(EXPLICIT)"를 나눠 보여준다. 중복 제거는 하지 않는다 — 집계 시 `COUNT(DISTINCT session_id)`로 세션 기준 수요를 함께 보여주면 충분하다(과설계 방지).

**Files:**
- Create: `backend/src/main/java/com/koscom/kopilot/demand/DemandRecorder.java`, `DemandService.java`, `CatalogRequestController.java`
- Modify: `backend/src/main/java/com/koscom/kopilot/chat/ToolDispatcher.java` (explain_recipe 시 AUTO 적재)
- Modify: `backend/src/test/java/com/koscom/kopilot/chat/ToolDispatcherTest.java` (DemandRecorder 스텁 주입 + AUTO 적재 검증)
- Test: `backend/src/test/java/com/koscom/kopilot/demand/DemandServiceTest.java`

**Interfaces:**
- Consumes: Task 1 `catalog_request` 테이블, Task 8 `GuideService`, Task 10 `ToolDispatcher`
- Produces:
  - `interface DemandRecorder { void record(String sessionId, String topic, String matchedApiIds, String source); }` — 상수 `AUTO`, `EXPLICIT`
  - `class DemandService implements DemandRecorder` — JDBC 적재, `topic`은 공백 정규화 후 255자 절단
  - HTTP: `POST /api/catalog-requests` body `{"sessionId","topic","matchedApiIds"}` → 201 (source=`EXPLICIT`)
  - `ToolDispatcher` 생성자에 `DemandRecorder` 추가 — `explain_recipe` 성공 시 source=`AUTO` 적재

- [ ] **Step 1: 실패하는 테스트 작성** — `DemandServiceTest.java`

```java
package com.koscom.kopilot.demand;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("fixture")
class DemandServiceTest {

    @Autowired DemandService demand;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clean() { jdbc.update("DELETE FROM catalog_request WHERE session_id LIKE 'test-%'"); }

    @Test
    void recordsAutoAndExplicitRowsWithSource() {
        demand.record("test-s1", "외국인 순매수 수급", "stock-investor", "AUTO");
        demand.record("test-s1", "외국인 순매수 수급", "stock-investor", "EXPLICIT");

        var rows = jdbc.queryForList(
                "SELECT source, topic, matched_api_ids FROM catalog_request WHERE session_id = ? ORDER BY id",
                "test-s1");
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).get("source")).isEqualTo("AUTO");
        assertThat(rows.get(1).get("source")).isEqualTo("EXPLICIT");
        assertThat(rows.get(0).get("topic")).isEqualTo("외국인 순매수 수급");
    }

    @Test
    void longTopicIsTruncatedToColumnLimit() {
        demand.record("test-s2", "가".repeat(400), null, "AUTO");
        String topic = jdbc.queryForObject(
                "SELECT topic FROM catalog_request WHERE session_id = ?", String.class, "test-s2");
        assertThat(topic).hasSize(255);
    }

    @Test
    void blankTopicIsIgnored() {
        demand.record("test-s3", "   ", null, "AUTO");
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM catalog_request WHERE session_id = ?", Integer.class, "test-s3");
        assertThat(count).isZero();
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

```bash
docker compose up -d && cd backend && ./gradlew test --tests '*DemandServiceTest'
```

Expected: 컴파일 에러로 FAIL.

- [ ] **Step 3: 구현**

`DemandRecorder.java`:

```java
package com.koscom.kopilot.demand;

/** ToolDispatcher가 의존하는 최소 인터페이스 — 단위 테스트에서 스텁으로 대체한다. */
public interface DemandRecorder {
    String AUTO = "AUTO";
    String EXPLICIT = "EXPLICIT";

    void record(String sessionId, String topic, String matchedApiIds, String source);
}
```

`DemandService.java`:

```java
package com.koscom.kopilot.demand;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 카탈로그 밖 질문의 수요를 적재한다.
 *  - AUTO     : 가이드 카드가 뜬 순간(= 카탈로그가 못 답한 질문) 자동 기록
 *  - EXPLICIT : 사용자가 "카탈로그 추가 요청" 버튼을 눌러 의사를 명시한 경우
 * 두 신호를 분리해 두면 Admin에서 "수요량"과 "강도"를 구분해 지표 확장 우선순위를 뽑을 수 있다.
 */
@Service
public class DemandService implements DemandRecorder {

    private static final int TOPIC_MAX = 255;

    private final JdbcTemplate jdbc;

    public DemandService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public void record(String sessionId, String topic, String matchedApiIds, String source) {
        if (topic == null || topic.isBlank()) return;
        String normalized = topic.trim().replaceAll("\\s+", " ");
        if (normalized.length() > TOPIC_MAX) normalized = normalized.substring(0, TOPIC_MAX);
        String apiIds = (matchedApiIds == null || matchedApiIds.isBlank()) ? null
                : (matchedApiIds.length() > 255 ? matchedApiIds.substring(0, 255) : matchedApiIds);
        try {
            jdbc.update("""
                INSERT INTO catalog_request(session_id, topic, matched_api_ids, source)
                VALUES (?, ?, ?, ?)
                """, sessionId, normalized, apiIds, source);
        } catch (RuntimeException e) {
            // 수요 적재 실패가 사용자 답변을 막아서는 안 된다
        }
    }
}
```

`CatalogRequestController.java` (Task 8에서 이관·확장):

```java
package com.koscom.kopilot.demand;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
public class CatalogRequestController {

    private final DemandRecorder demand;

    public CatalogRequestController(DemandRecorder demand) { this.demand = demand; }

    /** 가이드 카드의 "카탈로그 추가 요청" 버튼 — 기록만 한다(스펙 6절). */
    @PostMapping("/api/catalog-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public void request(@RequestBody Map<String, String> body) {
        demand.record(body.get("sessionId"), body.get("topic"),
                body.get("matchedApiIds"), DemandRecorder.EXPLICIT);
    }
}
```

`ToolDispatcher.java` 수정 — 생성자와 `explainRecipe`:

```java
    private final CatalogService catalog;
    private final GuideService guide;
    private final CardSink cards;
    private final DemandRecorder demand;

    public ToolDispatcher(CatalogService catalog, GuideService guide, CardSink cards, DemandRecorder demand) {
        this.catalog = catalog;
        this.guide = guide;
        this.cards = cards;
        this.demand = demand;
    }
```

```java
    private DispatchResult explainRecipe(String sessionId, JsonNode args) {
        String topic = args.path("topic").asText("");
        // LLM이 사용자 표현을 명세 용어로 확장해 넘긴 검색어 (예: "수급" → ["투자자별","순매수"])
        List<String> keywords = new ArrayList<>();
        args.path("keywords").forEach(n -> keywords.add(n.asText()));
        if (keywords.isEmpty() && !topic.isBlank()) keywords.add(topic);
        GuideService.GuideResult r = guide.recipeContext(topic, keywords);

        // 버튼 클릭 여부와 무관하게, 가이드 카드가 뜬 것 자체가 "카탈로그가 못 답한 수요"다
        String matchedIds = r.matched().stream()
                .map(com.koscom.kopilot.guide.ApiSpecEntry::apiId)
                .collect(java.util.stream.Collectors.joining(","));
        demand.record(sessionId, topic, matchedIds, DemandRecorder.AUTO);

        ObjectNode payload = mapper.createObjectNode().put("topic", topic);
        payload.set("matched", mapper.valueToTree(r.matched()));
        payload.set("catalog", mapper.valueToTree(r.catalog()));
        String json = payload.toString();
        return new DispatchResult(json, false, new DispatchResult.SsePush("guide", json));
    }
```

`dispatch()` 내 호출부도 `if (EXPLAIN_RECIPE.equals(toolName)) return explainRecipe(sessionId, args);` 로 교체한다.

`ToolDispatcherTest` 수정 — `dispatcher()` 팩토리에 스텁 주입:

```java
    private final List<String> recordedDemand = new ArrayList<>();

    private ToolDispatcher dispatcher() {
        ApiSpecIndex index = ApiSpecIndex.loadFromClasspath();
        ExecutorSupport support = new ExecutorSupport(new FixtureCheckApiClient(), TestStocks.resolver(), index);
        CatalogService catalog = new CatalogService(List.of(
                new ReturnGapExecutor(support), new VolatilityExecutor(support),
                new NavDisparityExecutor(support), new MaDisparityExecutor(support),
                new ReturnRankingExecutor(support), new PeriodSummaryExecutor(support)));
        CardSink sink = (sessionId, r) -> savedCards.add(r);
        DemandRecorder demand = (sessionId, topic, apiIds, source) ->
                recordedDemand.add(source + "|" + topic + "|" + apiIds);
        return new ToolDispatcher(catalog, new GuideService(index, FieldDictionary.loadFromClasspath()),
                sink, demand);
    }
```

기존 `explainRecipe_emitsGuideEvent_withCatalogAndMatches` 테스트 끝에 추가:

```java
        // 버튼 클릭 없이도 수요가 적재된다
        assertThat(recordedDemand).hasSize(1);
        assertThat(recordedDemand.get(0)).startsWith("AUTO|외국인 순매수 수급|").contains("stock-investor");
```

- [ ] **Step 4: 테스트 통과 확인**

```bash
./gradlew test --tests '*DemandServiceTest' --tests '*ToolDispatcherTest'
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat: record catalog demand on guide card and explicit request button"
```
---

### Task 12: 익명 세션 + Redis 대화 컨텍스트 저장소

**설계 판단 — 익명 세션 식별 방식은 "클라이언트 생성 UUID v4 + localStorage"를 채택한다.**

| 방식 | 장점 | 단점 | 판정 |
|---|---|---|---|
| 서블릿 세션(JSESSIONID) | 서버가 발급해 위조 어려움 | 쿠키 의존, SSE·CORS·Vite 프록시에서 잔가시, 인스턴스 확장 시 별도 처리 | ✗ |
| **클라이언트 UUID(localStorage) + URL path** | 기존 `POST /api/chat/{sessionId}` 계약 무변경, 서버 무상태(상태는 Redis), 새로고침·다중 탭 제어가 프론트에서 명시적, 데모 리셋("새 대화") 1줄 | 위조 가능(임의 세션 ID 접근) | **✓ 채택** |
| Spring Session + Redis | 표준적, 쿠키 자동 | 의존성·설정 추가 대비 얻는 게 없음(인증이 없어 보호할 주체가 없음) | ✗ (과설계) |

위조 리스크 평가: 인증·개인정보·과금이 없고 저장 데이터는 공개 시장 데이터 기반 계산 결과뿐이다. UUID v4는 추측 불가능하고 서버는 세션 목록을 노출하지 않는다(Admin 집계도 세션 ID를 노출하지 않음). 대신 **서버가 세션 ID 형식을 검증**해 Redis 키 인젝션·무한 키 증식을 막는다.

**Files:**
- Create: `backend/src/main/java/com/koscom/kopilot/chat/SessionIds.java`
- Create: `backend/src/main/java/com/koscom/kopilot/chat/ConversationStore.java`, `RedisConversationStore.java`, `ConversationCodec.java`
- Test: `backend/src/test/java/com/koscom/kopilot/chat/ConversationCodecTest.java`, `SessionIdsTest.java`

**Interfaces:**
- Consumes: Task 1 Redis 설정(`spring.data.redis.*`, `kopilot.session-ttl`, `kopilot.max-history-turns`)
- Produces:
  - `class SessionIds { static String requireValid(String sessionId); }` — 형식 위반 시 `IllegalArgumentException`
  - `interface ConversationStore { List<Message> load(String s); void save(String s, List<Message> m); void clear(String s); }`
  - `class ConversationCodec { String encode(List<Message>); List<Message> decode(String); List<Message> trimToRecent(List<Message>, int); }` — Spring AI `Message` ↔ 직렬화 DTO (**tool_use id 보존 필수**)
  - `class RedisConversationStore implements ConversationStore` — 키 `kopilot:session:{sessionId}`, TTL `kopilot.session-ttl`, 최근 `kopilot.max-history-turns` 턴만 유지
  - 영속 이력은 MySQL `chat_log`(`ChatLogService`)가 담당 — Redis는 **살아 있는 컨텍스트 전용**이라 유실돼도 서비스는 지속된다(맥락만 초기화)

- [ ] **Step 1: 실패하는 테스트 작성** — `ConversationCodecTest.java`

```java
package com.koscom.kopilot.chat;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class ConversationCodecTest {

    private final ConversationCodec codec = new ConversationCodec();

    @Test
    void roundTripsUserAssistantToolCallAndToolResponse() {
        List<Message> original = List.of(
                new UserMessage("삼성전자랑 코스피 수익률 갭"),
                new AssistantMessage("", java.util.Map.of(), List.of(
                        new AssistantMessage.ToolCall("call_1", "function", "return_gap",
                                "{\"target_a\":\"삼성전자\"}"))),
                new ToolResponseMessage(List.of(
                        new ToolResponseMessage.ToolResponse("call_1", "return_gap",
                                "{\"status\":\"ok\"}"))),
                new AssistantMessage("삼성전자가 코스피 대비 3.0%p 앞섭니다."));

        List<Message> restored = codec.decode(codec.encode(original));

        assertThat(restored).hasSize(4);
        AssistantMessage withCall = (AssistantMessage) restored.get(1);
        assertThat(withCall.hasToolCalls()).isTrue();
        // tool_use id 보존은 필수 — 어긋나면 Anthropic이 tool_result 매칭에 실패한다
        assertThat(withCall.getToolCalls().get(0).id()).isEqualTo("call_1");
        assertThat(withCall.getToolCalls().get(0).name()).isEqualTo("return_gap");

        ToolResponseMessage toolMsg = (ToolResponseMessage) restored.get(2);
        assertThat(toolMsg.getResponses().get(0).id()).isEqualTo("call_1");
        assertThat(restored.get(3).getText()).contains("3.0%p");
    }

    @Test
    void keepsOnlyRecentTurns() {
        List<Message> many = new java.util.ArrayList<>();
        for (int i = 0; i < 60; i++) many.add(new UserMessage("q" + i));
        String encoded = codec.encode(codec.trimToRecent(many, 20));
        assertThat(codec.decode(encoded)).hasSize(20);
        assertThat(codec.decode(encoded).get(0).getText()).isEqualTo("q40");
    }
}
```

`SessionIdsTest.java`:

```java
package com.koscom.kopilot.chat;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class SessionIdsTest {

    @Test
    void acceptsUuidAndDemoPrefixes() {
        assertThat(SessionIds.requireValid("11111111-1111-4111-8111-111111111111"))
                .isEqualTo("11111111-1111-4111-8111-111111111111");
        assertThat(SessionIds.requireValid("warmup-abc123")).isEqualTo("warmup-abc123");
    }

    @Test
    void rejectsInjectionShapedIds() {
        assertThatThrownBy(() -> SessionIds.requireValid("../../etc/passwd"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SessionIds.requireValid("a".repeat(200)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

```bash
./gradlew test --tests '*ConversationCodecTest' --tests '*SessionIdsTest'
```

Expected: 컴파일 에러로 FAIL.

- [ ] **Step 3: 구현**

`SessionIds.java`:

```java
package com.koscom.kopilot.chat;

import java.util.regex.Pattern;

/** 로그인 없는 MVP의 익명 세션 식별자 검증. Redis 키·DB 컬럼에 그대로 쓰이므로 형식을 강제한다. */
public final class SessionIds {

    private static final Pattern ALLOWED = Pattern.compile("^[A-Za-z0-9_-]{8,64}$");

    private SessionIds() {}

    public static String requireValid(String sessionId) {
        if (sessionId == null || !ALLOWED.matcher(sessionId).matches()) {
            throw new IllegalArgumentException("잘못된 세션 식별자");
        }
        return sessionId;
    }
}
```

`ConversationStore.java`:

```java
package com.koscom.kopilot.chat;

import org.springframework.ai.chat.messages.Message;

import java.util.List;

/**
 * 대화 컨텍스트 저장소.
 * Redis에 두는 이유: ①시연 중 동시 접속자 간 컨텍스트 격리 ②백엔드 재기동·다중 인스턴스에도 대화 유지
 *                    ③TTL로 자동 정리(인메모리 Map은 무한 증식).
 * 영속 이력은 MySQL chat_log가 담당하므로 Redis 유실은 서비스 중단이 아니다(맥락만 초기화).
 */
public interface ConversationStore {
    List<Message> load(String sessionId);
    void save(String sessionId, List<Message> messages);
    void clear(String sessionId);
}
```

`ConversationCodec.java`:

```java
package com.koscom.kopilot.chat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Spring AI Message ↔ JSON DTO 변환.
 * Message 구현체를 직접 직렬화하지 않는 이유: 라이브러리 내부 구조 변경에 저장 포맷이 끌려다니기 때문.
 * tool_use id는 반드시 보존한다 — Anthropic은 tool_use/tool_result 쌍의 id 일치를 요구한다.
 */
public class ConversationCodec {

    public record Turn(String role, String text, List<Call> calls, List<Result> results) {
        public record Call(String id, String name, String arguments) {}
        public record Result(String id, String name, String data) {}
    }

    private final ObjectMapper mapper = new ObjectMapper();

    public List<Message> trimToRecent(List<Message> messages, int max) {
        if (messages.size() <= max) return messages;
        return new ArrayList<>(messages.subList(messages.size() - max, messages.size()));
    }

    public String encode(List<Message> messages) {
        List<Turn> turns = new ArrayList<>();
        for (Message m : messages) {
            if (m instanceof UserMessage u) {
                turns.add(new Turn("user", u.getText(), List.of(), List.of()));
            } else if (m instanceof AssistantMessage a) {
                List<Turn.Call> calls = a.hasToolCalls()
                        ? a.getToolCalls().stream()
                            .map(c -> new Turn.Call(c.id(), c.name(), c.arguments())).toList()
                        : List.of();
                turns.add(new Turn("assistant", a.getText() == null ? "" : a.getText(), calls, List.of()));
            } else if (m instanceof ToolResponseMessage t) {
                List<Turn.Result> results = t.getResponses().stream()
                        .map(r -> new Turn.Result(r.id(), r.name(), r.responseData())).toList();
                turns.add(new Turn("tool", "", List.of(), results));
            }
        }
        try { return mapper.writeValueAsString(turns); }
        catch (Exception e) { throw new IllegalStateException("대화 컨텍스트 직렬화 실패", e); }
    }

    public List<Message> decode(String json) {
        if (json == null || json.isBlank()) return List.of();
        List<Turn> turns;
        try { turns = mapper.readValue(json, new TypeReference<List<Turn>>() {}); }
        catch (Exception e) { return List.of(); }   // 포맷 변경 시 맥락만 버리고 계속 동작

        List<Message> messages = new ArrayList<>();
        for (Turn t : turns) {
            switch (t.role()) {
                case "user" -> messages.add(new UserMessage(t.text()));
                case "assistant" -> {
                    List<AssistantMessage.ToolCall> calls = t.calls().stream()
                            .map(c -> new AssistantMessage.ToolCall(c.id(), "function", c.name(), c.arguments()))
                            .toList();
                    messages.add(new AssistantMessage(t.text(), Map.of(), calls));
                }
                case "tool" -> messages.add(new ToolResponseMessage(t.results().stream()
                        .map(r -> new ToolResponseMessage.ToolResponse(r.id(), r.name(), r.data()))
                        .toList()));
                default -> { }
            }
        }
        return messages;
    }
}
```

> 스파이크 검증 완료: `AssistantMessage(String, Map<String,Object>, List<ToolCall>)` 생성자와 `ToolResponse#responseData()` 접근자 모두 존재한다. `ToolCall`의 `type`은 `"function"` 고정.

`RedisConversationStore.java`:

```java
package com.koscom.kopilot.chat;

import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Service
public class RedisConversationStore implements ConversationStore {

    private static final String PREFIX = "kopilot:session:";

    private final StringRedisTemplate redis;
    private final ConversationCodec codec = new ConversationCodec();
    private final Duration ttl;
    private final int maxTurns;

    public RedisConversationStore(StringRedisTemplate redis,
                                  @Value("${kopilot.session-ttl}") Duration ttl,
                                  @Value("${kopilot.max-history-turns}") int maxTurns) {
        this.redis = redis;
        this.ttl = ttl;
        this.maxTurns = maxTurns;
    }

    @Override
    public List<Message> load(String sessionId) {
        try { return codec.decode(redis.opsForValue().get(PREFIX + SessionIds.requireValid(sessionId))); }
        catch (RuntimeException e) { return List.of(); }   // Redis 장애 시 맥락 없이 단발 응답
    }

    @Override
    public void save(String sessionId, List<Message> messages) {
        try {
            redis.opsForValue().set(PREFIX + SessionIds.requireValid(sessionId),
                    codec.encode(codec.trimToRecent(messages, maxTurns)), ttl);
        } catch (RuntimeException ignored) { }
    }

    @Override
    public void clear(String sessionId) {
        try { redis.delete(PREFIX + SessionIds.requireValid(sessionId)); }
        catch (RuntimeException ignored) { }
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

```bash
./gradlew test --tests '*ConversationCodecTest' --tests '*SessionIdsTest'
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat: anonymous session ids and redis-backed conversation context"
```
---

### Task 13: chat 모듈 — Spring AI 수동 tool 루프 + SSE 엔드포인트 + 시스템 프롬프트 + 로깅

Spring AI의 **자동 tool 실행을 끄고**(`internalToolExecutionEnabled=false`) 애플리케이션이 tool 호출을 직접 디스패치한다. 이유: tool 실행 도중 SSE로 카드/되묻기/가이드 이벤트를 즉시 프론트에 밀어내야 하는데, 자동 실행은 그 훅 지점을 주지 않는다. Task 10의 `ToolDispatcher`와 tool 스키마 설계는 그대로 보존하고 SDK 호출부만 Spring AI로 바꾼다.

> **스파이크 결과 (2026-07-22, spring-ai-bom 1.0.0 / Spring Boot 3.5.3 / Java 21)** — 아래 코드의 모든 Spring AI 시그니처를 `javap`와 실제 컴파일로 확정했다. 추측 없음:
>
> ```
> ToolCallback:            getToolDefinition() / call(String) / call(String, ToolContext)  ← call(String,ToolContext)는 default
> ToolDefinition:          name() / description() / inputSchema() / static builder()
> DefaultToolDefinition.Builder: name(String) / description(String) / inputSchema(String) / build()
> ToolCallingChatOptions.Builder: toolCallbacks(List<ToolCallback>) / internalToolExecutionEnabled(Boolean)
>                                 / model(String) / maxTokens(Integer) / temperature(Double) / build()
> AssistantMessage:        생성자 (String), (String,Map), (String,Map,List<ToolCall>) / getToolCalls() / hasToolCalls()
> AssistantMessage.ToolCall: record (id, type, name, arguments)   ← 4-arg, type은 "function"
> ToolResponseMessage:     생성자 (List<ToolResponse>) / getResponses()
> ToolResponseMessage.ToolResponse: record (id, name, responseData)   ← 3-arg
> ```
>
> **모델 ID는 런타임 옵션에 문자열로 직접 지정한다** — `ToolCallingChatOptions.Builder.model("claude-opus-4-8")`. enum에 의존하지 않으므로 최신 모델 지원 여부 문제가 없다.
>
> ⚠️ 미검증 1건: **tool 호출이 자동 실행되지 않고 되돌아오는지**(= `internalToolExecutionEnabled(false)`의 런타임 동작)와 tool 결과 재주입 후 최종 해설이 생성되는지는 유효한 `ANTHROPIC_API_KEY`가 없어 확인하지 못했다. 스파이크 프로젝트는 `scratchpad/spike/`에 있고 컴파일·빈 주입·요청 전송까지는 통과했다(HTTP 401에서 멈춤). **Task 13 Step 5에서 이 2가지를 최우선으로 확인할 것.**

**Files:**
- Create: `backend/src/main/java/com/koscom/kopilot/chat/KopilotTools.java`, `SystemPrompt.java`, `ChatLogService.java`, `ChatService.java`, `ChatController.java`, `ChatConfig.java`
- Test: `backend/src/test/java/com/koscom/kopilot/chat/KopilotToolsTest.java` (tool 스키마 생성만 단위 테스트; LLM 루프는 Step 5 수동 검증)

**Interfaces:**
- Consumes: Task 10 `ToolDispatcher`, Task 5 `CatalogService`(tool 스키마 소스), Task 12 `ConversationStore`·`SessionIds`
- Produces:
  - HTTP: `POST /api/chat/{sessionId}` body `{"message":"..."}` → `text/event-stream`
  - SSE 이벤트 순서: (`card`|`clarify`|`guide`)* → `text`(해설 전문 1건) → `done`. 실패 시 `error` 후 종료 (**기존 계약 그대로 — 프론트 무변경**)
  - `class KopilotTools { List<ToolCallback> build(); }` — 지표 6종 + explain_recipe + get_api_spec = tool 8개
  - LLM 설정: `spring.ai.anthropic.chat.options`(model `claude-opus-4-8`, maxTokens 4096), tool 루프 최대 8회

- [ ] **Step 1: 실패하는 테스트 작성** — `KopilotToolsTest.java`

```java
package com.koscom.kopilot.chat;

import com.koscom.kopilot.catalog.*;
import com.koscom.kopilot.checkapi.FixtureCheckApiClient;
import com.koscom.kopilot.guide.ApiSpecIndex;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class KopilotToolsTest {

    @Test
    void buildsEightToolDefinitionsWithNamesAndJsonSchema() {
        ApiSpecIndex index = ApiSpecIndex.loadFromClasspath();
        ExecutorSupport support = new ExecutorSupport(new FixtureCheckApiClient(), TestStocks.resolver(), index);
        CatalogService catalog = new CatalogService(List.of(
                new ReturnGapExecutor(support), new VolatilityExecutor(support),
                new NavDisparityExecutor(support), new MaDisparityExecutor(support),
                new ReturnRankingExecutor(support), new PeriodSummaryExecutor(support)));

        List<ToolCallback> tools = new KopilotTools(catalog).build();

        assertThat(tools).hasSize(8);
        assertThat(tools).extracting(t -> t.getToolDefinition().name()).containsExactlyInAnyOrder(
                "return_gap", "volatility", "nav_disparity", "ma_disparity",
                "return_ranking", "period_summary", "explain_recipe", "get_api_spec");

        String returnGapSchema = tools.stream()
                .filter(t -> t.getToolDefinition().name().equals("return_gap"))
                .findFirst().orElseThrow().getToolDefinition().inputSchema();
        assertThat(returnGapSchema)
                .contains("\"type\":\"object\"").contains("target_a").contains("required");
    }

    @Test
    void toolCallbacksRefuseAutomaticExecution() {
        ApiSpecIndex index = ApiSpecIndex.loadFromClasspath();
        ExecutorSupport support = new ExecutorSupport(new FixtureCheckApiClient(), TestStocks.resolver(), index);
        CatalogService catalog = new CatalogService(List.of(new ReturnGapExecutor(support)));

        ToolCallback any = new KopilotTools(catalog).build().get(0);
        // 실행은 ToolDispatcher 전담 — 자동 실행이 켜지면 조용히 우회되는 대신 즉시 실패해야 한다
        assertThatThrownBy(() -> any.call("{}")).isInstanceOf(IllegalStateException.class);
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

```bash
./gradlew test --tests '*KopilotToolsTest'
```

Expected: 컴파일 에러로 FAIL.

- [ ] **Step 3: 구현**

`KopilotTools.java`:

```java
package com.koscom.kopilot.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.koscom.kopilot.catalog.CatalogService;
import com.koscom.kopilot.catalog.MetricExecutor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 지표 실행기 6종 + 가이드 tool 2종을 Spring AI ToolCallback(스키마 전용)으로 변환한다.
 * 실제 실행은 ChatService의 수동 루프가 ToolDispatcher로 수행한다.
 */
public class KopilotTools {

    private final CatalogService catalog;
    private final ObjectMapper mapper = new ObjectMapper();

    public KopilotTools(CatalogService catalog) { this.catalog = catalog; }

    public List<ToolCallback> build() {
        List<ToolCallback> tools = new ArrayList<>();
        for (MetricExecutor e : catalog.all()) {
            tools.add(tool(e.toolName(), e.description(), e.inputSchemaProperties(), e.requiredParams()));
        }
        tools.add(tool(ToolDispatcher.EXPLAIN_RECIPE,
                "카탈로그 6개 지표로 답할 수 없는 데이터/지표 질문이거나, 사용자가 구현 방법·API 조합을 물을 때 사용. "
              + "관련 CHECK API 후보와 각 API가 반환하는 관련 필드를 찾아 반환한다. 이를 바탕으로 어떤 API를 어떤 "
              + "파라미터로 호출해 어떤 필드를 어떻게 조합·계산하면 되는지 레시피를 설명할 것. "
              + "거절 대신 항상 이 tool로 가이드를 제공한다.",
                Map.of(
                    "topic", Map.of("type", "string",
                        "description", "사용자가 원하는 데이터/지표에 대한 한국어 설명"),
                    "keywords", Map.of("type", "array", "items", Map.of("type", "string"),
                        "description", "검색어. 사용자의 구어 표현이 아니라 **금융 데이터 명세에 쓰일 법한 용어**로 "
                            + "변환해 2~4개 넣을 것. 예: \"수급 어때?\" → [\"투자자별\",\"순매수\"], "
                            + "\"외인 물량\" → [\"외국인\",\"순매수\"], \"공매 얼마나 쌓였어\" → [\"공매도\",\"잔고\"]")),
                List.of("topic", "keywords")));
        tools.add(tool(ToolDispatcher.GET_API_SPEC,
                "explain_recipe 결과의 catalog 목록에서 상세 명세가 더 필요한 API가 있을 때, apiId 배열로 상세를 조회한다.",
                Map.of("apiIds", Map.of("type", "array", "items", Map.of("type", "string"),
                        "description", "상세 명세를 조회할 apiId 목록")),
                List.of("apiIds")));
        return tools;
    }

    private ToolCallback tool(String name, String description,
                              Map<String, Object> props, List<String> required) {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        schema.set("properties", mapper.valueToTree(props));
        ArrayNode req = schema.putArray("required");
        required.forEach(req::add);

        ToolDefinition definition = ToolDefinition.builder()
                .name(name)
                .description(description)
                .inputSchema(schema.toString())
                .build();
        return new SchemaOnlyToolCallback(definition);
    }

    /** 스키마만 제공하는 ToolCallback. 자동 실행이 켜져 있으면 조용히 우회되지 않고 즉시 터진다. */
    static final class SchemaOnlyToolCallback implements ToolCallback {
        private final ToolDefinition definition;
        SchemaOnlyToolCallback(ToolDefinition definition) { this.definition = definition; }

        @Override public ToolDefinition getToolDefinition() { return definition; }

        @Override public String call(String toolInput) {
            throw new IllegalStateException(
                "tool 자동 실행이 활성화되어 있습니다. ToolCallingChatOptions.internalToolExecutionEnabled=false "
                + "설정을 확인하세요. tool=" + definition.name());
        }

        @Override public String call(String toolInput, ToolContext toolContext) { return call(toolInput); }
    }
}
```

> 위 시그니처는 스파이크에서 컴파일로 검증됐다. `ToolContext`는 `org.springframework.ai.chat.model.ToolContext`이고 `call(String, ToolContext)`는 default 메서드라 오버라이드는 선택이지만, 자동 실행 경로를 확실히 막기 위해 둘 다 구현한다.

`SystemPrompt.java`:

```java
package com.koscom.kopilot.chat;

import java.time.LocalDate;

public final class SystemPrompt {

    private SystemPrompt() {}

    public static String render(LocalDate today) {
        return """
            당신은 'Check Kopilot' — 코스콤 CHECK API 데이터를 다루는 트레이더용 정보 코파일럿이다.

            [역할과 원칙]
            - 수치 계산은 절대 직접 하지 않는다. 반드시 지표 tool을 호출하고, tool이 반환한 JSON의 수치만 인용해 해설한다.
            - tool 결과에 없는 수치를 지어내지 않는다. 해설은 2~4문장으로 간결하게.
            - 오늘 날짜는 %s 이다. "최근 한 달" 같은 상대 기간은 오늘 기준 ISO 날짜(from/to)로 변환해 tool에 전달한다.
            - 종목은 한글 이름 그대로 tool에 전달한다(코드 변환은 시스템이 수행).

            [되묻기]
            - tool이 status=ambiguous(종목 다건 매칭)를 반환하면, 후보 목록을 언급하며 어느 종목인지 한 문장으로 되묻는다.
              (후보 버튼은 화면에 자동 표시되므로 목록을 장황하게 나열하지 말 것)
            - 기간·대상이 질문에 없으면 tool을 추측 호출하지 말고 먼저 되묻는다.
            - status=error(검증 실패)면 그 원인을 자연어로 설명하고 올바른 입력을 유도한다.

            [가이드 모드]
            - 카탈로그 지표로 답할 수 없는 데이터 질문, 또는 구현 방법 질문에는 거절하지 말고 explain_recipe를 호출한다.
            - explain_recipe가 반환한 catalog/matched를 바탕으로 ①필요 API ②호출 파라미터 ③조합·계산 공식 ④예시 순서의
              레시피를 설명한다. 상세가 더 필요한 API는 get_api_spec으로 조회한다.

            [컴플라이언스 — 최우선]
            - 투자 판단·권유·전망("사야 돼?", "얼마까지 갈까?", 목표주가)은 절대 생성하지 않는다.
              해당 질문에는 판단 대신 참고 가능한 팩트 지표(수익률, 변동성 등)를 제안하는 정보성 답변으로 전환한다.
            - 모든 답변은 팩트 기반 정보 제공으로 한정한다.
            """.formatted(today);
    }
}
```

`ChatLogService.java`:

```java
package com.koscom.kopilot.chat;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ChatLogService {

    private final JdbcTemplate jdbc;

    public ChatLogService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void log(String sessionId, String role, String toolName, String content) {
        // 관측성(스펙 10절): 대화·tool 호출·에러 전부 기록 — 평가셋 원천 데이터이자 영속 대화 이력
        jdbc.update("INSERT INTO chat_log(session_id, role, tool_name, content) VALUES (?,?,?,?)",
                sessionId, role, toolName, content == null ? "" : content);
    }
}
```

`ChatService.java` — Spring AI 수동 tool 루프:

```java
package com.koscom.kopilot.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class ChatService {

    private static final int MAX_TOOL_ITERATIONS = 8;

    private final ChatModel chatModel;
    private final KopilotTools tools;
    private final ToolDispatcher dispatcher;
    private final ConversationStore conversations;
    private final ChatLogService logs;
    private final ObjectMapper mapper = new ObjectMapper();

    public ChatService(ChatModel chatModel, KopilotTools tools, ToolDispatcher dispatcher,
                       ConversationStore conversations, ChatLogService logs) {
        this.chatModel = chatModel;
        this.tools = tools;
        this.dispatcher = dispatcher;
        this.conversations = conversations;
        this.logs = logs;
    }

    public void handle(String sessionId, String userMessage, SseEmitter emitter) {
        try {
            logs.log(sessionId, "user", null, userMessage);

            // 대화 컨텍스트: Redis에서 복원 → 이번 턴 진행 → 종료 시 통째로 저장
            List<Message> messages = new ArrayList<>();
            messages.add(new SystemMessage(SystemPrompt.render(LocalDate.now())));
            messages.addAll(conversations.load(sessionId));
            messages.add(new UserMessage(userMessage));

            // 핵심: 자동 tool 실행 OFF — tool 호출은 아래 루프가 직접 디스패치한다
            ToolCallingChatOptions options = ToolCallingChatOptions.builder()
                    .toolCallbacks(tools.build())
                    .internalToolExecutionEnabled(false)
                    .model("claude-opus-4-8")
                    .maxTokens(4096)
                    .build();

            String finalText = "";
            for (int iteration = 0; iteration < MAX_TOOL_ITERATIONS; iteration++) {
                ChatResponse response = chatModel.call(new Prompt(messages, options));
                AssistantMessage assistant = response.getResult().getOutput();
                messages.add(assistant);

                if (assistant.getText() != null && !assistant.getText().isBlank()) {
                    finalText = assistant.getText();
                }
                if (!assistant.hasToolCalls()) break;      // tool 호출 없음 → 최종 답변

                List<ToolResponseMessage.ToolResponse> toolResponses = new ArrayList<>();
                for (AssistantMessage.ToolCall call : assistant.getToolCalls()) {
                    DispatchResult r = runTool(sessionId, call.name(), call.arguments(), emitter);
                    toolResponses.add(new ToolResponseMessage.ToolResponse(
                            call.id(), call.name(), r.toolResultJson()));
                }
                messages.add(new ToolResponseMessage(toolResponses));
            }

            conversations.save(sessionId, messages.subList(1, messages.size()));  // system 제외
            logs.log(sessionId, "assistant", null, finalText);
            send(emitter, "text", mapper.createObjectNode().put("text", finalText).toString());
            send(emitter, "done", "{}");
            emitter.complete();
        } catch (Exception e) {
            logs.log(sessionId, "error", null, String.valueOf(e));
            try {
                send(emitter, "error", mapper.createObjectNode()
                        .put("message", "요청 처리에 실패했습니다. 다시 시도해 주세요.").toString());
            } catch (Exception ignored) { }
            emitter.completeWithError(e);
        }
    }

    private DispatchResult runTool(String sessionId, String name, String argumentsJson, SseEmitter emitter) {
        try {
            String raw = (argumentsJson == null || argumentsJson.isBlank()) ? "{}" : argumentsJson;
            JsonNode args = mapper.readTree(raw);
            logs.log(sessionId, "tool_call", name, args.toString());
            DispatchResult r = dispatcher.dispatch(sessionId, name, args);
            logs.log(sessionId, "tool_result", name, r.toolResultJson());
            if (r.push() != null) send(emitter, r.push().event(), r.push().dataJson());
            return r;
        } catch (Exception e) {
            logs.log(sessionId, "error", name, String.valueOf(e));
            return new DispatchResult(
                    "{\"status\":\"error\",\"code\":\"INTERNAL\",\"message\":\"tool 실행 실패\"}",
                    true, null);
        }
    }

    private void send(SseEmitter emitter, String event, String dataJson) throws java.io.IOException {
        emitter.send(SseEmitter.event().name(event)
                .data(dataJson, org.springframework.http.MediaType.APPLICATION_JSON));
    }
}
```

> 위 시그니처는 전부 스파이크에서 검증됐다(`AssistantMessage.ToolCall`은 4-arg 레코드, `ToolResponseMessage.ToolResponse`는 `(id, name, responseData)` 3-arg 레코드).
>
> yml 옵션 병합에 기대지 않도록 **`options` 빌더에 `.model("claude-opus-4-8").maxTokens(4096)`을 직접 붙인다.**

`ChatConfig.java`:

```java
package com.koscom.kopilot.chat;

import com.koscom.kopilot.catalog.CatalogService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class ChatConfig {

    // ChatModel(AnthropicChatModel) 빈은 spring-ai-starter-model-anthropic 자동 구성이 제공한다.
    // API 키는 spring.ai.anthropic.api-key ← 환경변수 ANTHROPIC_API_KEY

    @Bean
    public KopilotTools kopilotTools(CatalogService catalog) { return new KopilotTools(catalog); }

    @Bean(destroyMethod = "shutdown")
    public ExecutorService chatExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
```

`ChatController.java`:

```java
package com.koscom.kopilot.chat;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.ExecutorService;

@RestController
public class ChatController {

    private static final long TIMEOUT_MS = 180_000L;

    private final ChatService chatService;
    private final ExecutorService chatExecutor;

    public ChatController(ChatService chatService, ExecutorService chatExecutor) {
        this.chatService = chatService;
        this.chatExecutor = chatExecutor;
    }

    @PostMapping(value = "/api/chat/{sessionId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@PathVariable String sessionId, @RequestBody Map<String, String> body) {
        String safeSessionId = SessionIds.requireValid(sessionId);   // 익명 세션 ID 형식 검증
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        String message = body.getOrDefault("message", "");
        chatExecutor.submit(() -> chatService.handle(safeSessionId, message, emitter));
        return emitter;
    }
}
```

- [ ] **Step 4: 단위 테스트 통과 확인**

```bash
./gradlew test --tests '*KopilotToolsTest'
```

Expected: BUILD SUCCESSFUL, 2 tests passed.

- [ ] **Step 5: 실 LLM 연동 수동 검증** (fixture 프로파일 — CHECK API 없이 동작)

**최우선 확인 2가지** (스파이크에서 API 키 부재로 미검증):
1. tool 호출이 **자동 실행되지 않고** `AssistantMessage.getToolCalls()`로 되돌아오는가 — `SchemaOnlyToolCallback`이 `IllegalStateException`을 던지면 자동 실행이 켜진 것이다.
2. `ToolResponseMessage` 재주입 후 최종 해설이 정상 생성되는가 (tool_use/tool_result id 매칭).

`scratchpad/spike/`의 최소 재현 프로젝트로 먼저 확인하면 더 빠르다.

```bash
docker compose up -d
cd backend
SPRING_PROFILES_ACTIVE=fixture ANTHROPIC_API_KEY=sk-ant-... ./gradlew bootRun &
sleep 25
curl -N -X POST localhost:8080/api/chat/11111111-1111-4111-8111-111111111111 \
  -H 'Content-Type: application/json' \
  -d '{"message":"삼성전자랑 코스피, 2026-07-13부터 2026-07-17까지 수익률 갭 알려줘"}'
```

Expected(순서): `event: card` + MetricResult JSON(수익률 갭 3.0 포함) → `event: text` + 해설 → `event: done`.

```bash
curl -N -X POST localhost:8080/api/chat/22222222-2222-4222-8222-222222222222 \
  -H 'Content-Type: application/json' \
  -d '{"message":"에코 지난주 시세 요약해줘"}'
```

Expected: `event: clarify` + 후보 3종 → `event: text`(되묻기) → `event: done`.

```bash
curl -N -X POST localhost:8080/api/chat/33333333-3333-4333-8333-333333333333 \
  -H 'Content-Type: application/json' \
  -d '{"message":"삼성전자 지금 사야 돼?"}'
```

Expected: tool 호출 없이 `event: text`(투자 판단 거절 + 정보성 전환) → `event: done`.

멀티턴 컨텍스트(Redis) 확인 — 같은 세션에 이어서 질문:

```bash
curl -N -X POST localhost:8080/api/chat/11111111-1111-4111-8111-111111111111 \
  -H 'Content-Type: application/json' -d '{"message":"같은 기간으로 현대차는?"}'
docker compose exec redis redis-cli KEYS 'kopilot:session:*'
docker compose exec db mysql -ukopilot -pkopilot kopilot \
  -e "SELECT role, tool_name, LEFT(content,60) FROM chat_log ORDER BY id;"
```

Expected: 두 번째 질문이 앞 대화를 이어받아 `period_summary`/`return_gap`을 호출, Redis에 세션 키 존재, `chat_log`에 user/tool_call/tool_result/assistant 행 누적.

- [ ] **Step 6: Commit**

```bash
git add -A && git commit -m "feat: chat module with spring ai manual tool loop, sse events and redis-backed context"
```
---

### Task 14: tool 선택 평가셋 (의도 인식 정확도 %)

**Files:**
- Create: `backend/src/test/resources/eval-cases.yaml`
- Test: `backend/src/test/java/com/koscom/kopilot/chat/EvalRunner.java`

**Interfaces:**
- Consumes: Task 13 `KopilotTools`·`SystemPrompt`, Spring AI `ChatModel` (실 LLM API 사용 — `ANTHROPIC_API_KEY` 필요)
- Produces: `RUN_EVAL=true` 환경변수로만 실행되는 배치 테스트. 콘솔에 케이스별 결과와 **의도 인식 정확도 %** 출력, `build/eval-report.txt` 저장 (발표용 정량 지표 — 스펙 6절)
- 주의: `@SpringBootTest`이므로 MySQL·Redis(docker compose)가 떠 있어야 한다. CHECK API는 `fixture` 프로파일로 우회한다

- [ ] **Step 1: 평가 케이스 작성** — `eval-cases.yaml` (`expect`: tool 이름 또는 `no_tool`. `no_tool` = 되묻기/거절이 정답. `params`는 선택 — tool 입력에 포함돼야 하는 부분 문자열):

```yaml
cases:
  # ── 수익률 갭
  - q: "삼성전자랑 코스피, 최근 한 달 수익률 갭 알려줘"
    expect: return_gap
    params: { target_a: "삼성전자", target_b: "코스피" }
  - q: "현대차와 코스피 올해 수익률 차이 얼마나 나?"
    expect: return_gap
  - q: "에코프로비엠이 코스닥보다 최근 두 달 동안 얼마나 더 올랐어?"
    expect: return_gap
  - q: "카카오는 코스피 대비 최근 일주일 수익률이 어때?"
    expect: return_gap
  # ── 변동성
  - q: "에코프로랑 에코프로비엠 변동성 비교해줘"
    expect: volatility
  - q: "삼성전자 최근 3개월 변동성 알려줘"
    expect: volatility
  - q: "현대차랑 카카오 중에 어느 쪽이 더 출렁여? 최근 한 달 기준"
    expect: volatility
  # ── ETF 괴리율
  - q: "TIGER 미국S&P500 괴리율 알려줘"
    expect: nav_disparity
    params: { target: "TIGER" }
  - q: "KODEX 200 시장가가 NAV 대비 얼마나 벌어져 있어?"
    expect: nav_disparity
  - q: "삼성전자 괴리율 알려줘"
    expect: nav_disparity          # tool 선택은 맞음 — 실행 단계에서 NOT_ETF 안내가 정답
  # ── 이동평균 이격도
  - q: "카카오 20일선 이격도 알려줘"
    expect: ma_disparity
    params: { target: "카카오" }
  - q: "현대차가 60일 이동평균선보다 얼마나 위에 있어?"
    expect: ma_disparity
  - q: "삼성전자 이격도 봐줘"
    expect: ma_disparity
  # ── 상대수익률 랭킹
  - q: "에코프로, 엘앤에프, 포스코퓨처엠 3개월 수익률 순위 매겨줘"
    expect: return_ranking
  - q: "삼성전자 현대차 카카오 중에 올해 누가 제일 많이 올랐어?"
    expect: return_ranking
  - q: "LG에너지솔루션이랑 포스코퓨처엠, 에코프로비엠 최근 한 달 수익률 줄 세워봐"
    expect: return_ranking
  # ── 기간 시세 요약
  - q: "현대차 올해 최고가 최저가 수익률 알려줘"
    expect: period_summary
    params: { target: "현대차" }
  - q: "삼성전자 최근 6개월 시세 요약해줘"
    expect: period_summary
  - q: "카카오 이번 분기 최고가가 언제 얼마였어?"
    expect: period_summary
  # ── 가이드 (카탈로그 밖 → explain_recipe)
  - q: "삼성전자 외국인 순매수 동향 알려줘"
    expect: explain_recipe
  - q: "PER 밴드 차트를 그리려면 CHECK API를 어떻게 조합해야 해?"
    expect: explain_recipe
  - q: "배당수익률은 어떤 API로 구할 수 있어?"
    expect: explain_recipe
  - q: "공매도 잔고 데이터도 뽑을 수 있어?"
    expect: explain_recipe
  # ── 모호 → 되묻기 (no_tool)
  - q: "수익률 갭 알려줘"
    expect: no_tool
  - q: "2차전지 종목들 수익률 순위 알려줘"
    expect: no_tool               # 테마명 자동 확장은 로드맵 — 종목 나열을 되물어야 함
  - q: "변동성 비교 좀"
    expect: no_tool
  - q: "괴리율 알려줘"
    expect: no_tool
  # ── 투자 판단 → 거절·정보성 전환 (no_tool)
  - q: "삼성전자 지금 사야 돼?"
    expect: no_tool
  - q: "다음 달 코스피 어디까지 갈 것 같아?"
    expect: no_tool
  - q: "에코프로 목표주가 얼마로 보면 돼?"
    expect: no_tool
```

(30케이스 시드. 데모 리허설 중 발견되는 오인식 유형을 케이스로 추가해 30~50개를 유지한다 — 스펙 11절.)

- [ ] **Step 2: EvalRunner 작성**

```java
package com.koscom.kopilot.chat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;
import org.yaml.snakeyaml.Yaml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * tool 선택 평가 — 실 LLM 호출(케이스당 1회, tool 실행 없음).
 * 실행: docker compose up -d && RUN_EVAL=true ANTHROPIC_API_KEY=... ./gradlew test --tests '*EvalRunner'
 */
@SpringBootTest
@ActiveProfiles("fixture")
@EnabledIfEnvironmentVariable(named = "RUN_EVAL", matches = "true")
class EvalRunner {

    @Autowired ChatModel chatModel;
    @Autowired KopilotTools tools;

    @Test
    @SuppressWarnings("unchecked")
    void measureIntentAccuracy() throws Exception {
        ToolCallingChatOptions options = ToolCallingChatOptions.builder()
                .toolCallbacks(tools.build())
                .internalToolExecutionEnabled(false)
                .model("claude-opus-4-8")
                .maxTokens(4096)
                .build();

        Map<String, Object> root;
        try (var in = new ClassPathResource("eval-cases.yaml").getInputStream()) {
            root = new Yaml().load(in);
        }
        List<Map<String, Object>> cases = (List<Map<String, Object>>) root.get("cases");

        StringBuilder report = new StringBuilder();
        int correct = 0;
        for (Map<String, Object> c : cases) {
            String q = (String) c.get("q");
            String expect = (String) c.get("expect");
            Map<String, Object> expectedParams = (Map<String, Object>) c.getOrDefault("params", Map.of());

            List<Message> messages = List.of(
                    new SystemMessage(SystemPrompt.render(LocalDate.now())),
                    new UserMessage(q));
            ChatResponse response = chatModel.call(new Prompt(messages, options));
            AssistantMessage out = response.getResult().getOutput();

            String actual = out.hasToolCalls() ? out.getToolCalls().get(0).name() : "no_tool";
            String input = out.hasToolCalls() ? String.valueOf(out.getToolCalls().get(0).arguments()) : "";

            boolean ok = actual.equals(expect);
            if (ok && out.hasToolCalls() && !expectedParams.isEmpty()) {
                for (Object v : expectedParams.values()) {
                    if (!input.contains(String.valueOf(v))) { ok = false; break; }
                }
            }
            if (ok) correct++;
            report.append(ok ? "PASS  " : "FAIL  ")
                    .append("expect=").append(expect).append("  actual=").append(actual)
                    .append("  q=").append(q).append('\n');
        }

        double accuracy = 100.0 * correct / cases.size();
        report.append("\n의도 인식 정확도: %.1f%% (%d/%d)%n".formatted(accuracy, correct, cases.size()));
        System.out.print(report);
        Files.createDirectories(Path.of("build"));
        Files.writeString(Path.of("build/eval-report.txt"), report.toString());

        assertThat(correct).isGreaterThan(0);   // 게이트가 아니라 측정 — 리포트가 산출물
    }
}
```

- [ ] **Step 3: 평가 실행 및 리포트 확인**

```bash
docker compose up -d
RUN_EVAL=true ANTHROPIC_API_KEY=sk-ant-... ./gradlew test --tests '*EvalRunner'
cat build/eval-report.txt
```

Expected: 케이스별 PASS/FAIL과 마지막 줄 `의도 인식 정확도: NN.N% (n/30)`. FAIL 케이스를 보고 tool description·시스템 프롬프트를 튜닝 → 재실행으로 회귀 측정(스펙 13절). 목표 85% 이상 권장.

- [ ] **Step 4: Commit**

```bash
git add -A && git commit -m "test: tool selection eval set with intent accuracy report"
```
---

### Task 15: Admin 수요조사 API (토큰 접근 제어)

**설계 판단 — 접근 제어는 "환경변수 공유 시크릿 + 404 위장"을 채택한다.**

무방비는 데모 URL이 공유되는 순간 아무나 내부 로그성 데이터를 보게 되어 발표 리스크가 된다. Spring Security 도입은 MVP 인증 제외 결정과 충돌하고 공수 대비 이득이 없다. 따라서 `/api/admin/**`에 `X-Admin-Token` 헤더(또는 브라우저 링크용 `?token=` 쿼리) 하나를 검사하는 필터로 끝낸다. 실패 시 401이 아니라 **404**를 반환해 엔드포인트 존재 자체를 숨긴다. 응답에는 세션 ID·질문 원문 같은 식별성 정보를 넣지 않고 **집계값만** 노출한다.

**Files:**
- Create: `backend/src/main/java/com/koscom/kopilot/demand/AdminController.java`, `AdminTokenFilter.java`, `DemandSummary.java`
- Test: `backend/src/test/java/com/koscom/kopilot/demand/AdminControllerTest.java`

**Interfaces:**
- Consumes: Task 1 `catalog_request`·`chat_log`·`card` 테이블, `admin.token` 설정
- Produces:
  - `record DemandSummary(String topic, long requestCount, long explicitCount, long sessionCount, String matchedApiIds, String lastAt)`
  - HTTP `GET /api/admin/demand/summary?limit=50` → `List<DemandSummary>` (요청수 내림차순)
  - HTTP `GET /api/admin/stats` → `{"questionCount","cardCount","guideCount","catalogCoverageRate"}` (`catalogCoverageRate = 1 − guideCount / questionCount`, 발표용 "카탈로그가 답한 비율")
  - 토큰 불일치·누락 시 모든 `/api/admin/**`는 404

- [ ] **Step 1: 실패하는 테스트 작성** — `AdminControllerTest.java`

```java
package com.koscom.kopilot.demand;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("fixture")
@TestPropertySource(properties = "admin.token=test-token")
class AdminControllerTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        jdbc.update("DELETE FROM catalog_request WHERE session_id LIKE 'admin-test-%'");
        jdbc.update("INSERT INTO catalog_request(session_id, topic, matched_api_ids, source) VALUES (?,?,?,?)",
                "admin-test-1", "외국인 수급", "stock-investor", "AUTO");
        jdbc.update("INSERT INTO catalog_request(session_id, topic, matched_api_ids, source) VALUES (?,?,?,?)",
                "admin-test-2", "외국인 수급", "stock-investor", "EXPLICIT");
    }

    @Test
    void summaryAggregatesByTopic_whenTokenValid() throws Exception {
        mvc.perform(get("/api/admin/demand/summary").header("X-Admin-Token", "test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].topic").value("외국인 수급"))
                .andExpect(jsonPath("$[0].requestCount").value(2))
                .andExpect(jsonPath("$[0].explicitCount").value(1))
                .andExpect(jsonPath("$[0].sessionCount").value(2));
    }

    @Test
    void acceptsQueryParamToken_forBrowserLinks() throws Exception {
        mvc.perform(get("/api/admin/demand/summary").param("token", "test-token"))
                .andExpect(status().isOk());
    }

    @Test
    void hidesEndpointWithNotFound_whenTokenMissingOrWrong() throws Exception {
        mvc.perform(get("/api/admin/demand/summary")).andExpect(status().isNotFound());
        mvc.perform(get("/api/admin/demand/summary").header("X-Admin-Token", "nope"))
                .andExpect(status().isNotFound());
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

```bash
./gradlew test --tests '*AdminControllerTest'
```

Expected: FAIL.

- [ ] **Step 3: 구현**

`AdminTokenFilter.java`:

```java
package com.koscom.kopilot.demand;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 로그인 인증이 없는 MVP에서 Admin 조회 화면을 보호하는 최소 장치.
 * 실패 시 401이 아니라 404를 반환해 엔드포인트 존재 자체를 노출하지 않는다.
 * (정식 인증·권한은 스펙 6절 MVP 제외 항목 — 로드맵)
 */
@Component
public class AdminTokenFilter extends OncePerRequestFilter {

    private final String token;

    public AdminTokenFilter(@Value("${admin.token}") String token) { this.token = token; }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/admin/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String provided = request.getHeader("X-Admin-Token");
        if (provided == null) provided = request.getParameter("token");
        if (token == null || token.isBlank() || !token.equals(provided)) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        chain.doFilter(request, response);
    }
}
```

`DemandSummary.java`:

```java
package com.koscom.kopilot.demand;

public record DemandSummary(String topic, long requestCount, long explicitCount,
                            long sessionCount, String matchedApiIds, String lastAt) {}
```

`AdminController.java`:

```java
package com.koscom.kopilot.demand;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 수요조사 조회 화면용 집계 API.
 * 식별 가능 정보를 노출하지 않기 위해 세션 ID·질문 원문은 반환하지 않고 집계값만 돌려준다.
 */
@RestController
public class AdminController {

    private final JdbcTemplate jdbc;

    public AdminController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping("/api/admin/demand/summary")
    public List<DemandSummary> summary(@RequestParam(defaultValue = "50") int limit) {
        int capped = Math.max(1, Math.min(limit, 200));
        return jdbc.query("""
            SELECT topic,
                   COUNT(*)                                              AS request_count,
                   SUM(CASE WHEN source = 'EXPLICIT' THEN 1 ELSE 0 END)  AS explicit_count,
                   COUNT(DISTINCT session_id)                            AS session_count,
                   SUBSTRING_INDEX(GROUP_CONCAT(COALESCE(matched_api_ids, '')
                                   ORDER BY created_at DESC SEPARATOR '||'), '||', 1) AS matched_api_ids,
                   MAX(created_at)                                       AS last_at
              FROM catalog_request
             GROUP BY topic
             ORDER BY request_count DESC, last_at DESC
             LIMIT ?
            """, (rs, i) -> new DemandSummary(
                        rs.getString("topic"),
                        rs.getLong("request_count"),
                        rs.getLong("explicit_count"),
                        rs.getLong("session_count"),
                        rs.getString("matched_api_ids"),
                        String.valueOf(rs.getTimestamp("last_at"))),
                capped);
    }

    @GetMapping("/api/admin/stats")
    public Map<String, Object> stats() {
        long questions = count("SELECT COUNT(*) FROM chat_log WHERE role = 'user'");
        long cards = count("SELECT COUNT(*) FROM card");
        long guides = count("SELECT COUNT(*) FROM catalog_request WHERE source = 'AUTO'");
        double coverage = questions == 0 ? 0.0
                : Math.round((1.0 - (double) guides / questions) * 1000) / 10.0;
        return Map.of(
                "questionCount", questions,
                "cardCount", cards,
                "guideCount", guides,
                "catalogCoverageRate", coverage);   // % — "카탈로그가 답한 비율"
    }

    private long count(String sql) {
        Long v = jdbc.queryForObject(sql, Long.class);
        return v == null ? 0L : v;
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

```bash
./gradlew test --tests '*AdminControllerTest'
```

Expected: BUILD SUCCESSFUL, 3 tests passed.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat: admin demand summary api with shared-token access control"
```
---

### Task 16: 프론트엔드 스캐폴딩 + 채팅 UI + SSE 클라이언트

**Files:**
- Create: `check-kopilot/frontend/` (Vite react-ts 템플릿), `vite.config.ts`, `src/types.ts`, `src/api.ts`, `src/session.ts`, `src/App.tsx`, `src/App.css`, `src/test-setup.ts`
- Test: `frontend/src/__tests__/api.test.ts`

**Interfaces:**
- Consumes: Task 13 SSE 계약 (`card`/`clarify`/`guide`/`text`/`error`/`done`)
- Produces:
  - `src/types.ts` — 백엔드 `MetricResult` JSON을 미러링한 타입 (이후 컴포넌트가 사용)
  - `streamChat(sessionId, message, onEvent): Promise<void>` / `consumeSseBuffer(buffer, onEvent): string`
  - `ChatItem` 유니온 타입과 App의 메시지 스트림 상태 관리

- [ ] **Step 1: 스캐폴딩**

```bash
cd /Users/jinhyeok/dev/koscom/check-kopilot
npm create vite@latest frontend -- --template react-ts
cd frontend && npm install
npm install recharts
npm install -D vitest jsdom @testing-library/react @testing-library/jest-dom
```

`vite.config.ts` (프록시 + vitest 설정):

```ts
/// <reference types="vitest/config" />
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: { '/api': 'http://localhost:8080' },
  },
  test: {
    environment: 'jsdom',
    setupFiles: './src/test-setup.ts',
  },
})
```

`src/test-setup.ts`:

```ts
import '@testing-library/jest-dom'
```

`package.json`의 scripts에 `"test": "vitest run"` 추가.

- [ ] **Step 2: 실패하는 테스트 작성** — `src/__tests__/api.test.ts` (SSE 버퍼 파서)

```ts
import { describe, it, expect, vi } from 'vitest'
import { consumeSseBuffer } from '../api'

describe('consumeSseBuffer', () => {
  it('완결된 이벤트를 파싱하고 미완결 잔여분을 반환한다', () => {
    const onEvent = vi.fn()
    const buffer =
      'event: card\ndata: {"cardId":"abc"}\n\n' +
      'event: text\ndata: {"text":"해설"}\n\n' +
      'event: done\ndata: {'          // 미완결 조각
    const rest = consumeSseBuffer(buffer, onEvent)

    expect(onEvent).toHaveBeenCalledTimes(2)
    expect(onEvent).toHaveBeenNthCalledWith(1, 'card', { cardId: 'abc' })
    expect(onEvent).toHaveBeenNthCalledWith(2, 'text', { text: '해설' })
    expect(rest).toBe('event: done\ndata: {')
  })

  it('data 없는 조각은 무시한다', () => {
    const onEvent = vi.fn()
    consumeSseBuffer('event: ping\n\n', onEvent)
    expect(onEvent).not.toHaveBeenCalled()
  })
})
```

- [ ] **Step 3: 테스트 실패 확인**

```bash
npm test
```

Expected: FAIL (`consumeSseBuffer` 미존재).

- [ ] **Step 4: 구현**

`src/types.ts` (백엔드 `MetricResult` 미러 — Task 4 스키마와 필드명 1:1):

```ts
export interface Target { code: string; name: string }
export interface Headline { label: string; value: number; unit: string }
export interface ChartPoint { label: string; value: number }
export interface ChartSeries { name: string; points: ChartPoint[] }
export interface ChartSpec { chartType: 'line' | 'bar'; series: ChartSeries[] }
export interface ApiCall { api: string; request: string; specUrl: string }
export interface EvidenceRow { date: string; value: number }
export interface RawSeries { name: string; rows: EvidenceRow[] }
export interface CalcStep { label: string; detail: string }
export interface Evidence {
  apiCalls: ApiCall[]; rawData: RawSeries[]; formula: string; steps: CalcStep[]
}
export interface MetricCardData {
  cardId: string; metric: string; title: string; from: string; to: string
  targets: Target[]; headline: Headline[]; chart: ChartSpec; evidence: Evidence
}

export interface ClarifyCandidate { code: string; name: string; market: string }
export interface ClarifyData { query: string; candidates: ClarifyCandidate[] }

export interface ApiSpecEntry {
  apiId: string; name: string; path: string; summary: string
  params: string[]; docUrl: string; keywords: string[]
}
export interface CatalogLine { apiId: string; name: string; summary: string }
export interface GuideData { topic: string; matched: ApiSpecEntry[]; catalog: CatalogLine[] }

export type ChatItem =
  | { kind: 'user'; text: string }
  | { kind: 'assistant'; text: string }
  | { kind: 'error'; text: string; retry: string }   // LLM 장애 → 메시지 + 재시도(스펙 10절)
  | { kind: 'card'; card: MetricCardData }
  | { kind: 'clarify'; clarify: ClarifyData }
  | { kind: 'guide'; guide: GuideData }
```

`src/api.ts`:

```ts
export type SseHandler = (event: string, data: unknown) => void

/** SSE 텍스트 버퍼에서 완결된 이벤트(\n\n 구분)를 파싱해 콜백, 미완결 잔여분 반환 */
export function consumeSseBuffer(buffer: string, onEvent: SseHandler): string {
  const parts = buffer.split('\n\n')
  const rest = parts.pop() ?? ''
  for (const part of parts) {
    let event = 'message'
    let data = ''
    for (const line of part.split('\n')) {
      if (line.startsWith('event:')) event = line.slice(6).trim()
      else if (line.startsWith('data:')) data += line.slice(5).trim()
    }
    if (data) onEvent(event, JSON.parse(data))
  }
  return rest
}

/** POST 기반 SSE 스트림 소비 (EventSource는 GET 전용이라 fetch 스트림 사용) */
export async function streamChat(sessionId: string, message: string, onEvent: SseHandler): Promise<void> {
  const res = await fetch(`/api/chat/${sessionId}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ message }),
  })
  if (!res.ok || !res.body) throw new Error(`chat 요청 실패: ${res.status}`)
  const reader = res.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  for (;;) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    buffer = consumeSseBuffer(buffer, onEvent)
  }
}
```

`src/session.ts`:

```ts
const KEY = 'kopilot.sessionId'

/** 로그인 없는 MVP의 대화 컨텍스트 키. UUID v4를 localStorage에 보관해 새로고침에도 이어지게 한다. */
export function getSessionId(): string {
  const saved = localStorage.getItem(KEY)
  if (saved) return saved
  const fresh = crypto.randomUUID()
  localStorage.setItem(KEY, fresh)
  return fresh
}

/** 데모 리셋용 — 새 대화 시작 */
export function resetSessionId(): string {
  const fresh = crypto.randomUUID()
  localStorage.setItem(KEY, fresh)
  return fresh
}
```

`src/App.tsx` (이 태스크 시점에는 card/clarify/guide 이벤트를 원시 JSON 텍스트로 표시 — Task 17/18에서 전용 컴포넌트로 교체):

```tsx
import { useRef, useState } from 'react'
import { streamChat } from './api'
import { getSessionId, resetSessionId } from './session'
import type { ChatItem, ClarifyData, GuideData, MetricCardData } from './types'
import './App.css'

export default function App() {
  const [items, setItems] = useState<ChatItem[]>([])
  const [input, setInput] = useState('')
  const [busy, setBusy] = useState(false)
  const sessionId = useRef(getSessionId())

  const push = (item: ChatItem) => setItems(prev => [...prev, item])

  async function send(text: string) {
    const message = text.trim()
    if (!message || busy) return
    setInput('')
    setBusy(true)
    push({ kind: 'user', text: message })
    try {
      await streamChat(sessionId.current, message, (event, data) => {
        if (event === 'text') push({ kind: 'assistant', text: (data as { text: string }).text })
        else if (event === 'card') push({ kind: 'card', card: data as MetricCardData })
        else if (event === 'clarify') push({ kind: 'clarify', clarify: data as ClarifyData })
        else if (event === 'guide') push({ kind: 'guide', guide: data as GuideData })
        else if (event === 'error') push({ kind: 'error', text: (data as { message: string }).message, retry: message })
      })
    } catch {
      push({ kind: 'error', text: '요청에 실패했습니다.', retry: message })
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="app">
      <header>
        <h1>Check Kopilot</h1>
        <button className="btn secondary" onClick={() => {
          sessionId.current = resetSessionId()
          setItems([])
        }}>새 대화</button>
      </header>
      <main className="stream">
        {items.map((item, i) => (
          <div key={i} className={`item ${item.kind}`}>
            {item.kind === 'user' && <div className="bubble user">{item.text}</div>}
            {item.kind === 'assistant' && <div className="bubble assistant">{item.text}</div>}
            {item.kind === 'error' && (
              <div className="bubble assistant">
                {item.text}{' '}
                <button onClick={() => send(item.retry)} disabled={busy}>다시 시도</button>
              </div>
            )}
            {/* Task 17/18에서 전용 카드 컴포넌트로 교체 */}
            {item.kind === 'card' && <pre className="raw">{JSON.stringify(item.card, null, 2)}</pre>}
            {item.kind === 'clarify' && <pre className="raw">{JSON.stringify(item.clarify, null, 2)}</pre>}
            {item.kind === 'guide' && <pre className="raw">{JSON.stringify(item.guide, null, 2)}</pre>}
          </div>
        ))}
        {busy && <div className="bubble assistant">답변 생성 중…</div>}
      </main>
      <footer>
        <form onSubmit={e => { e.preventDefault(); send(input) }}>
          <input value={input} onChange={e => setInput(e.target.value)}
                 placeholder="예: 삼성전자랑 코스피, 최근 한 달 수익률 갭 알려줘" />
          <button type="submit" disabled={busy}>전송</button>
        </form>
        <p className="disclaimer">본 자료는 AI가 시장 데이터 기반으로 생성한 정보성 자료이며 투자권유가 아닙니다.</p>
      </footer>
    </div>
  )
}
```

`src/App.css` (최소 스타일 — 데모 품질은 Task 18에서 다듬는다):

```css
.app { max-width: 860px; margin: 0 auto; display: flex; flex-direction: column; min-height: 100vh; }
.stream { flex: 1; display: flex; flex-direction: column; gap: 12px; padding: 16px 0; }
.bubble { padding: 10px 14px; border-radius: 12px; max-width: 80%; white-space: pre-wrap; }
.bubble.user { align-self: flex-end; background: #2563eb; color: #fff; }
.bubble.assistant { align-self: flex-start; background: #f1f5f9; color: #111; }
.item.user { display: flex; justify-content: flex-end; }
.raw { font-size: 11px; background: #f8fafc; overflow-x: auto; padding: 8px; }
footer form { display: flex; gap: 8px; }
footer input { flex: 1; padding: 10px; }
.disclaimer { font-size: 12px; color: #64748b; text-align: center; }
```

(Vite 템플릿의 `index.css` 기본 데모 스타일은 내용 비우거나 폰트/배경만 남긴다.)

- [ ] **Step 5: 테스트·수동 확인**

```bash
npm test          # Expected: 2 passed
npm run dev       # 백엔드(fixture 프로파일) 켠 상태에서 localhost:5173 접속
```

브라우저에서 "삼성전자랑 코스피, 2026-07-13부터 2026-07-17까지 수익률 갭 알려줘" 입력 → 카드 원시 JSON + 해설 텍스트 + 하단 고지 문구 확인.

추가 확인: ① 브라우저 새로고침 후 이어서 질문 → 앞 대화 맥락이 유지되는지(Redis 컨텍스트). ② "새 대화" 버튼 클릭 → 세션 ID가 바뀌고 맥락이 초기화되는지.

- [ ] **Step 6: Commit**

```bash
git add -A && git commit -m "feat: react chat ui with post-based sse client"
```
---

### Task 17: 지표 답변 카드 (핵심 수치 · Recharts 차트 · 근거 패널 · xlsx 다운로드)

**Files:**
- Create: `frontend/src/components/MetricCard.tsx`, `EvidencePanel.tsx`
- Modify: `frontend/src/App.tsx` (card 원시 JSON → `<MetricCard>`), `frontend/src/App.css`
- Test: `frontend/src/components/__tests__/MetricCard.test.tsx`

**Interfaces:**
- Consumes: Task 16 `types.ts`, Task 9 `GET /api/cards/{cardId}/xlsx`
- Produces: `<MetricCard card={MetricCardData} />` — 헤더(제목·기간·대상 칩) / 핵심 수치 / 차트(line|bar) / 접이식 근거 패널 / xlsx 다운로드 버튼

- [ ] **Step 1: 실패하는 테스트 작성** — `MetricCard.test.tsx`

```tsx
import { render, screen } from '@testing-library/react'
import { describe, it, expect } from 'vitest'
import MetricCard from '../MetricCard'
import type { MetricCardData } from '../../types'

const card: MetricCardData = {
  cardId: 'abcd1234-0000-0000-0000-000000000000',
  metric: 'RETURN_GAP',
  title: '삼성전자 vs 코스피 수익률 갭 (2026-07-13 ~ 2026-07-17)',
  from: '2026-07-13', to: '2026-07-17',
  targets: [{ code: '005930', name: '삼성전자' }, { code: 'KOSPI', name: '코스피' }],
  headline: [
    { label: '삼성전자 기간수익률', value: 5.0, unit: '%' },
    { label: '코스피 기간수익률', value: 2.0, unit: '%' },
    { label: '수익률 갭', value: 3.0, unit: '%p' },
  ],
  chart: {
    chartType: 'line',
    series: [
      { name: '삼성전자', points: [{ label: '2026-07-13', value: 0 }, { label: '2026-07-17', value: 5 }] },
      { name: '코스피', points: [{ label: '2026-07-13', value: 0 }, { label: '2026-07-17', value: 2 }] },
    ],
  },
  evidence: {
    apiCalls: [{ api: '일별 시세 조회', request: '삼성전자(005930) 2026-07-13 ~ 2026-07-17', specUrl: 'https://checkapi.koscom.co.kr/docs/stock-daily' }],
    rawData: [{ name: '삼성전자', rows: [{ date: '2026-07-13', value: 100 }, { date: '2026-07-17', value: 105 }] }],
    formula: '수익률 갭(%p) = A 기간수익률 − B 기간수익률',
    steps: [{ label: '수익률 갭', detail: '5.0 − 2.0 = 3.0%p' }],
  },
}

describe('MetricCard', () => {
  it('제목·핵심 수치·근거·다운로드 링크를 렌더한다', () => {
    render(<MetricCard card={card} />)
    expect(screen.getByText(/삼성전자 vs 코스피 수익률 갭/)).toBeInTheDocument()
    expect(screen.getByText('수익률 갭')).toBeInTheDocument()
    expect(screen.getByText('3')).toBeInTheDocument()          // toLocaleString(3.0) === '3'
    expect(screen.getByText(/수익률 갭\(%p\) = A 기간수익률/)).toBeInTheDocument()
    const link = screen.getByRole('link', { name: /xlsx/i })
    expect(link).toHaveAttribute('href', `/api/cards/${card.cardId}/xlsx`)
    // 명세 링크
    expect(screen.getByRole('link', { name: /일별 시세 조회/ }))
      .toHaveAttribute('href', expect.stringContaining('checkapi.koscom.co.kr'))
  })
})
```

- [ ] **Step 2: 테스트 실패 확인**

```bash
npm test
```

Expected: FAIL (`MetricCard` 미존재).

- [ ] **Step 3: 구현**

`src/components/EvidencePanel.tsx`:

```tsx
import type { Evidence } from '../types'

export default function EvidencePanel({ evidence }: { evidence: Evidence }) {
  return (
    <details className="evidence">
      <summary>근거 보기 (호출 API · 원본 수치 · 공식 · 계산 과정)</summary>

      <h4>호출 API</h4>
      <ul>
        {evidence.apiCalls.map((c, i) => (
          <li key={i}>
            <a href={c.specUrl} target="_blank" rel="noreferrer">{c.api}</a>
            <span className="muted"> — {c.request}</span>
          </li>
        ))}
      </ul>

      <h4>원본 수치</h4>
      {evidence.rawData.map((s, i) => (
        <table key={i} className="raw-table">
          <caption>{s.name}</caption>
          <thead><tr><th>일자</th><th>값</th></tr></thead>
          <tbody>
            {s.rows.map((r, j) => (
              <tr key={j}><td>{r.date}</td><td>{r.value.toLocaleString()}</td></tr>
            ))}
          </tbody>
        </table>
      ))}

      <h4>공식</h4>
      <p className="formula">{evidence.formula}</p>

      <h4>계산 과정</h4>
      <ol>
        {evidence.steps.map((s, i) => (
          <li key={i}><strong>{s.label}</strong>: {s.detail}</li>
        ))}
      </ol>
    </details>
  )
}
```

`src/components/MetricCard.tsx`:

```tsx
import {
  Bar, BarChart, CartesianGrid, Legend, Line, LineChart,
  ResponsiveContainer, Tooltip, XAxis, YAxis,
} from 'recharts'
import type { ChartSpec, MetricCardData } from '../types'
import EvidencePanel from './EvidencePanel'

const COLORS = ['#2563eb', '#dc2626', '#059669', '#d97706', '#7c3aed']

/** [{label, 시리즈명: 값}] 형태로 병합 — Recharts 데이터 포맷 */
function mergeSeries(chart: ChartSpec): Record<string, string | number>[] {
  const byLabel = new Map<string, Record<string, string | number>>()
  for (const s of chart.series) {
    for (const p of s.points) {
      const row = byLabel.get(p.label) ?? { label: p.label }
      row[s.name] = p.value
      byLabel.set(p.label, row)
    }
  }
  return [...byLabel.values()]
}

function Chart({ chart }: { chart: ChartSpec }) {
  const data = mergeSeries(chart)
  if (data.length === 0) return null
  return (
    <ResponsiveContainer width="100%" height={220}>
      {chart.chartType === 'bar' ? (
        <BarChart data={data}>
          <CartesianGrid strokeDasharray="3 3" />
          <XAxis dataKey="label" fontSize={11} /><YAxis fontSize={11} /><Tooltip /><Legend />
          {chart.series.map((s, i) => (
            <Bar key={s.name} dataKey={s.name} fill={COLORS[i % COLORS.length]} />
          ))}
        </BarChart>
      ) : (
        <LineChart data={data}>
          <CartesianGrid strokeDasharray="3 3" />
          <XAxis dataKey="label" fontSize={11} /><YAxis fontSize={11} /><Tooltip /><Legend />
          {chart.series.map((s, i) => (
            <Line key={s.name} dataKey={s.name} stroke={COLORS[i % COLORS.length]} dot={false} />
          ))}
        </LineChart>
      )}
    </ResponsiveContainer>
  )
}

export default function MetricCard({ card }: { card: MetricCardData }) {
  return (
    <div className="metric-card">
      <div className="card-header">
        <h3>{card.title}</h3>
        <div className="chips">
          {card.targets.map(t => <span key={t.code} className="chip">{t.name} {t.code}</span>)}
          <span className="chip period">{card.from} ~ {card.to}</span>
        </div>
      </div>

      <div className="headline">
        {card.headline.map((h, i) => (
          <div key={i} className="stat">
            <div className="stat-label">{h.label}</div>
            <div className="stat-value">{h.value.toLocaleString()}<small>{h.unit}</small></div>
          </div>
        ))}
      </div>

      <Chart chart={card.chart} />
      <EvidencePanel evidence={card.evidence} />

      <div className="actions">
        <a className="btn" href={`/api/cards/${card.cardId}/xlsx`} download>xlsx 다운로드</a>
      </div>
    </div>
  )
}
```

`App.tsx` 수정 — card 분기를 교체:

```tsx
// import MetricCard from './components/MetricCard' 추가 후:
{item.kind === 'card' && <MetricCard card={item.card} />}
```

`App.css`에 카드 스타일 추가:

```css
.metric-card { border: 1px solid #e2e8f0; border-radius: 14px; padding: 16px; background: #fff;
               box-shadow: 0 1px 3px rgba(0,0,0,.06); max-width: 100%; }
.chips { display: flex; gap: 6px; flex-wrap: wrap; margin: 6px 0; }
.chip { background: #eef2ff; color: #3730a3; border-radius: 999px; padding: 2px 10px; font-size: 12px; }
.chip.period { background: #f1f5f9; color: #475569; }
.headline { display: flex; gap: 20px; flex-wrap: wrap; margin: 12px 0; }
.stat-label { font-size: 12px; color: #64748b; }
.stat-value { font-size: 22px; font-weight: 700; }
.stat-value small { font-size: 12px; margin-left: 2px; color: #64748b; }
.evidence { margin-top: 10px; font-size: 13px; }
.evidence summary { cursor: pointer; color: #2563eb; }
.raw-table { border-collapse: collapse; margin: 6px 0; }
.raw-table td, .raw-table th { border: 1px solid #e2e8f0; padding: 2px 10px; font-size: 12px; }
.formula { font-family: monospace; background: #f8fafc; padding: 6px 10px; border-radius: 6px; }
.actions { margin-top: 10px; }
.btn { display: inline-block; background: #2563eb; color: #fff; border-radius: 8px;
       padding: 8px 14px; text-decoration: none; font-size: 13px; }
.muted { color: #64748b; }
```

- [ ] **Step 4: 테스트 통과 + 수동 확인**

```bash
npm test    # Expected: 전체 통과
```

브라우저에서 수익률 갭 질문 → 카드(수치·차트·근거 패널 접기·xlsx 버튼) 렌더 확인. xlsx 버튼 클릭 → 파일 다운로드 후 열어 시트 3개와 카드 수치 일치 확인.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat: metric answer card with chart, evidence panel and xlsx download"
```
---

### Task 18: 가이드(레시피) 카드 + 종목 되묻기 칩

**Files:**
- Create: `frontend/src/components/GuideCard.tsx`, `ClarifyChips.tsx`
- Modify: `frontend/src/App.tsx` (guide/clarify 원시 JSON → 컴포넌트, 칩 클릭 → 재질문), `frontend/src/App.css`
- Test: `frontend/src/components/__tests__/GuideCard.test.tsx`

**Interfaces:**
- Consumes: Task 16 `GuideData`/`ClarifyData`, Task 11 `POST /api/catalog-requests`
- Produces:
  - `<GuideCard guide={GuideData} sessionId={string} />` — 필요 API 목록(명세 링크) + 전체 카탈로그 접기 + "카탈로그 추가 요청" 버튼(기록만)
  - `<ClarifyChips clarify={ClarifyData} onPick={(c: ClarifyCandidate) => void} />` — 클릭 한 번으로 종목 선택
  - 칩 클릭 시 App이 `"{종목명}({코드}) 기준으로 진행해줘"` 메시지를 같은 세션에 자동 전송

- [ ] **Step 1: 실패하는 테스트 작성** — `GuideCard.test.tsx`

```tsx
import { render, screen } from '@testing-library/react'
import { describe, it, expect } from 'vitest'
import GuideCard from '../GuideCard'
import type { GuideData } from '../../types'

const guide: GuideData = {
  topic: '외국인 순매수 수급',
  matched: [{
    apiId: 'stock-investor', name: '투자자별 매매동향 조회', path: '/stock/investor',
    summary: '종목별 기관·외국인·개인 순매수 동향을 조회한다',
    params: ['symbol', 'fromDate', 'toDate'],
    docUrl: 'https://checkapi.koscom.co.kr/docs/stock-investor', keywords: ['수급'],
  }],
  catalog: [{ apiId: 'stock-daily', name: '주식 일별 시세 조회', summary: '일별 OHLCV' }],
}

describe('GuideCard', () => {
  it('필요 API·명세 링크·파라미터·추가 요청 버튼을 렌더한다', () => {
    render(<GuideCard guide={guide} sessionId="s1" />)
    expect(screen.getByText(/이 질문에 쓸 수 있는 CHECK API/)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /투자자별 매매동향 조회/ }))
      .toHaveAttribute('href', expect.stringContaining('stock-investor'))
    expect(screen.getByText(/symbol, fromDate, toDate/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /카탈로그 추가 요청/ })).toBeInTheDocument()
  })
})
```

- [ ] **Step 2: 테스트 실패 확인**

```bash
npm test
```

Expected: FAIL (`GuideCard` 미존재).

- [ ] **Step 3: 구현**

`src/components/GuideCard.tsx`:

```tsx
import { useState } from 'react'
import type { GuideData } from '../types'

export default function GuideCard({ guide, sessionId }: { guide: GuideData; sessionId: string }) {
  const [requested, setRequested] = useState(false)

  async function requestCatalog() {
    await fetch('/api/catalog-requests', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        sessionId,
        topic: guide.topic,
        matchedApiIds: guide.matched.map(a => a.apiId).join(','),
      }),
    })
    setRequested(true)
  }

  return (
    <div className="guide-card">
      <h3>이 질문에 쓸 수 있는 CHECK API</h3>
      <p className="muted">카탈로그 지표 밖 질문 — 아래 API 조합 레시피는 해설을 참고하세요.</p>
      <ul>
        {guide.matched.map(api => (
          <li key={api.apiId} className="guide-api">
            <a href={api.docUrl} target="_blank" rel="noreferrer">{api.name}</a>
            <code className="muted"> {api.path}</code>
            <div>{api.summary}</div>
            {api.params.length > 0 && <div className="muted">파라미터: {api.params.join(', ')}</div>}
          </li>
        ))}
      </ul>
      <details>
        <summary>다른 후보 API ({guide.catalog.length}건)</summary>
        <ul>
          {guide.catalog.map(c => (
            <li key={c.apiId}><strong>{c.name}</strong> <span className="muted">— {c.summary}</span></li>
          ))}
        </ul>
      </details>
      <button className="btn secondary" onClick={requestCatalog} disabled={requested}>
        {requested ? '요청 접수됨' : '이 지표 카탈로그 추가 요청'}
      </button>
    </div>
  )
}
```

`src/components/ClarifyChips.tsx`:

```tsx
import type { ClarifyCandidate, ClarifyData } from '../types'

export default function ClarifyChips(
  { clarify, onPick }: { clarify: ClarifyData; onPick: (c: ClarifyCandidate) => void },
) {
  return (
    <div className="clarify">
      <span className="muted">'{clarify.query}' 검색 결과 — 종목을 선택하세요:</span>
      <div className="chips">
        {clarify.candidates.map(c => (
          <button key={c.code} className="chip clickable" onClick={() => onPick(c)}>
            {c.name} <small>{c.code} · {c.market}</small>
          </button>
        ))}
      </div>
    </div>
  )
}
```

`App.tsx` 수정 — import 추가 후 분기 교체:

```tsx
{item.kind === 'guide' && <GuideCard guide={item.guide} sessionId={sessionId.current} />}
{item.kind === 'clarify' && (
  <ClarifyChips clarify={item.clarify}
    onPick={c => send(`${c.name}(${c.code}) 기준으로 진행해줘`)} />
)}
```

`App.css` 추가:

```css
.guide-card { border: 1px solid #fde68a; background: #fffbeb; border-radius: 14px; padding: 16px; }
.guide-api { margin-bottom: 8px; }
.btn.secondary { background: #f59e0b; border: none; cursor: pointer; }
.chip.clickable { border: 1px solid #c7d2fe; background: #fff; cursor: pointer; }
.chip.clickable:hover { background: #eef2ff; }
```

- [ ] **Step 4: 테스트 통과 + 수동 확인**

```bash
npm test    # Expected: 전체 통과
```

브라우저 확인: ① "에코 지난주 시세 요약해줘" → 칩 3개 → 클릭 → 카드 응답. ② "삼성전자 외국인 순매수 알려줘" → 가이드 카드 + 해설 레시피 + 추가 요청 버튼 동작(`catalog_request` 테이블 행 확인).

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat: guide recipe card and stock clarify chips"
```
---

### Task 19: Admin 수요조사 화면 (프론트)

**Files:**
- Create: `frontend/src/admin/AdminPage.tsx`, `frontend/src/admin/adminApi.ts`
- Modify: `frontend/src/main.tsx` (해시 라우팅 분기), `frontend/src/App.css`
- Test: `frontend/src/admin/__tests__/AdminPage.test.tsx`

**Interfaces:**
- Consumes: Task 15의 `GET /api/admin/demand/summary`, `GET /api/admin/stats`
- Produces:
  - `#/admin?k=<ADMIN_TOKEN>` 해시 라우트로 진입하는 단일 페이지 (react-router 미도입 — 의존성 0)
  - `<AdminPage token={string} />` — 상단 통계 4개 + 수요 상위 테이블(주제/요청수/명시요청/세션수/관련 API/최근 시각)
  - 토큰 누락·오류 시 "접근 권한이 없습니다" 안내만 표시(엔드포인트 정보 비노출)

- [ ] **Step 1: 실패하는 테스트 작성** — `AdminPage.test.tsx`

```tsx
import { render, screen, waitFor } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import AdminPage from '../AdminPage'

beforeEach(() => {
  vi.stubGlobal('fetch', vi.fn((url: string) => {
    if (String(url).includes('/stats')) {
      return Promise.resolve({ ok: true, json: () => Promise.resolve({
        questionCount: 20, cardCount: 14, guideCount: 6, catalogCoverageRate: 70,
      })})
    }
    return Promise.resolve({ ok: true, json: () => Promise.resolve([
      { topic: '외국인 수급', requestCount: 5, explicitCount: 2, sessionCount: 4,
        matchedApiIds: 'stock-investor', lastAt: '2026-07-22 10:00:00' },
    ])})
  }))
})

describe('AdminPage', () => {
  it('통계와 수요 상위 항목을 렌더한다', async () => {
    render(<AdminPage token="test-token" />)
    await waitFor(() => expect(screen.getByText('외국인 수급')).toBeInTheDocument())
    expect(screen.getByText(/카탈로그 응답률/)).toBeInTheDocument()
    expect(screen.getByText('70%')).toBeInTheDocument()
    expect(screen.getByText('stock-investor')).toBeInTheDocument()
  })

  it('토큰이 없으면 접근 안내만 보여준다', () => {
    render(<AdminPage token="" />)
    expect(screen.getByText(/접근 권한이 없습니다/)).toBeInTheDocument()
    expect(fetch).not.toHaveBeenCalled()
  })
})
```

- [ ] **Step 2: 테스트 실패 확인**

```bash
npm test
```

Expected: FAIL (`AdminPage` 미존재).

- [ ] **Step 3: 구현**

`src/admin/adminApi.ts`:

```ts
export interface DemandSummary {
  topic: string
  requestCount: number
  explicitCount: number
  sessionCount: number
  matchedApiIds: string | null
  lastAt: string
}

export interface AdminStats {
  questionCount: number
  cardCount: number
  guideCount: number
  catalogCoverageRate: number
}

function headers(token: string) {
  return { 'X-Admin-Token': token }
}

export async function fetchDemandSummary(token: string): Promise<DemandSummary[]> {
  const res = await fetch('/api/admin/demand/summary?limit=50', { headers: headers(token) })
  if (!res.ok) throw new Error('unauthorized')
  return res.json()
}

export async function fetchStats(token: string): Promise<AdminStats> {
  const res = await fetch('/api/admin/stats', { headers: headers(token) })
  if (!res.ok) throw new Error('unauthorized')
  return res.json()
}

/** '#/admin?k=xxxx' 에서 토큰 추출 */
export function tokenFromHash(hash: string): string {
  const q = hash.split('?')[1] ?? ''
  return new URLSearchParams(q).get('k') ?? ''
}
```

`src/admin/AdminPage.tsx`:

```tsx
import { useEffect, useState } from 'react'
import { fetchDemandSummary, fetchStats } from './adminApi'
import type { AdminStats, DemandSummary } from './adminApi'

export default function AdminPage({ token }: { token: string }) {
  const [rows, setRows] = useState<DemandSummary[]>([])
  const [stats, setStats] = useState<AdminStats | null>(null)
  const [denied, setDenied] = useState(false)

  useEffect(() => {
    if (!token) { setDenied(true); return }
    Promise.all([fetchDemandSummary(token), fetchStats(token)])
      .then(([r, s]) => { setRows(r); setStats(s) })
      .catch(() => setDenied(true))
  }, [token])

  if (denied) return <div className="admin"><p>접근 권한이 없습니다.</p></div>

  return (
    <div className="admin">
      <h1>지표 수요 대시보드</h1>
      <p className="muted">
        카탈로그 밖 질문(가이드 카드 발생)을 자동 수집한 결과입니다. 다음에 구현할 지표의 우선순위 근거로 사용합니다.
      </p>

      {stats && (
        <div className="admin-stats">
          <div className="stat"><div className="stat-label">총 질문</div><div className="stat-value">{stats.questionCount}</div></div>
          <div className="stat"><div className="stat-label">지표 카드 응답</div><div className="stat-value">{stats.cardCount}</div></div>
          <div className="stat"><div className="stat-label">가이드(미지원) 응답</div><div className="stat-value">{stats.guideCount}</div></div>
          <div className="stat"><div className="stat-label">카탈로그 응답률</div><div className="stat-value">{stats.catalogCoverageRate}%</div></div>
        </div>
      )}

      <table className="raw-table admin-table">
        <thead>
          <tr>
            <th>요청 주제</th><th>요청 수</th><th>명시 요청</th><th>세션 수</th><th>관련 CHECK API</th><th>최근</th>
          </tr>
        </thead>
        <tbody>
          {rows.map(r => (
            <tr key={r.topic}>
              <td>{r.topic}</td>
              <td>{r.requestCount}</td>
              <td>{r.explicitCount}</td>
              <td>{r.sessionCount}</td>
              <td>{r.matchedApiIds ?? '-'}</td>
              <td className="muted">{r.lastAt}</td>
            </tr>
          ))}
        </tbody>
      </table>
      {rows.length === 0 && <p className="muted">아직 수집된 수요가 없습니다.</p>}
    </div>
  )
}
```

`src/main.tsx`:

```tsx
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import App from './App'
import AdminPage from './admin/AdminPage'
import { tokenFromHash } from './admin/adminApi'
import './index.css'

// react-router 없이 해시 라우팅 1분기 — 데모 규모에 의존성 추가는 과설계
const isAdmin = window.location.hash.startsWith('#/admin')

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    {isAdmin ? <AdminPage token={tokenFromHash(window.location.hash)} /> : <App />}
  </StrictMode>,
)
```

`App.css` 추가:

```css
.admin { max-width: 960px; margin: 0 auto; padding: 24px; }
.admin-stats { display: flex; gap: 28px; margin: 16px 0 24px; }
.admin-table { width: 100%; }
.admin-table th { background: #f8fafc; text-align: left; }
```

- [ ] **Step 4: 테스트 통과 + 수동 확인**

```bash
npm test    # Expected: 전체 통과
```

브라우저 확인: ① 채팅 화면에서 "삼성전자 외국인 순매수 알려줘"(가이드 카드) 2~3회 실행 → ② `http://localhost:5173/#/admin?k=kopilot-demo` 접속 → 해당 주제가 상위에 집계되고 "카탈로그 응답률"이 계산되는지 확인 → ③ `k`를 틀린 값으로 바꾸면 "접근 권한이 없습니다"만 뜨는지 확인.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat: admin demand dashboard page behind hash route"
```
---

### Task 20: E2E 데모 준비 (캐시 워밍업, README, 리허설)

**Files:**
- Create: `check-kopilot/scripts/warmup.sh`
- Create: `check-kopilot/README.md`

**Interfaces:**
- Consumes: 전체 시스템
- Produces: 데모 시나리오(스펙 12절) 전체가 실데이터(또는 캐시/픽스처 폴백)로 재현 가능한 상태 + 실행 문서

- [ ] **Step 1: 워밍업 스크립트 작성** — `scripts/warmup.sh` (데모 질문을 미리 흘려 ① Redis 단기 캐시 예열 ② MySQL `check_fallback`에 영속 스냅샷 적재 → 데모 중 API 장애 시 폴백)

```bash
#!/usr/bin/env bash
# 데모 직전 실행: 백엔드(실 API 프로파일)가 :8080에 떠 있어야 한다.
set -euo pipefail
BASE=http://localhost:8080
COMPOSE="$(dirname "$0")/../docker-compose.yml"
SESSION="warmup-$(uuidgen | tr 'A-Z' 'a-z')"

questions=(
  "삼성전자랑 코스피, 최근 한 달 수익률 갭 알려줘"
  "에코프로랑 에코프로비엠 최근 석 달 변동성 비교해줘"
  "TIGER 미국S&P500 최근 한 달 괴리율 알려줘"
  "카카오 20일선 이격도 알려줘"
  "에코프로, 엘앤에프, 포스코퓨처엠 3개월 수익률 순위 매겨줘"
  "현대차 올해 최고가 최저가 수익률 알려줘"
)

for q in "${questions[@]}"; do
  echo ">>> $q"
  curl -sN -X POST "$BASE/api/chat/$SESSION" \
    -H 'Content-Type: application/json' \
    -d "{\"message\":\"$q\"}" | tail -1
  echo
done

echo "폴백 스냅샷(MySQL check_fallback) 행 수:"
docker compose -f "$COMPOSE" exec -T db \
  mysql -ukopilot -pkopilot kopilot -N -e "SELECT COUNT(*) FROM check_fallback;"

echo "단기 캐시(Redis) 키 수:"
docker compose -f "$COMPOSE" exec -T redis redis-cli --scan --pattern 'checkapi:*' | wc -l

echo "주의: 워밍업 세션의 대화 맥락이 데모에 섞이지 않도록, 데모 시작 전 브라우저에서 '새 대화'를 누른다."
```

```bash
chmod +x scripts/warmup.sh
```

- [ ] **Step 2: README 작성** — `check-kopilot/README.md`

```markdown
# Check Kopilot

트레이더용 자연어 CHECK API 코파일럿. 질문 → LLM(Spring AI · Claude)이 지표 tool 선택 →
백엔드 Java가 CHECK API 호출·계산 → 근거(호출 API·원본 수치·공식·중간값)와 차트, xlsx가 달린 답변 카드.

## 실행

```bash
docker compose up -d                       # MySQL 8 + Redis 7
cd backend
ANTHROPIC_API_KEY=... CHECK_API_KEY=... ./gradlew bootRun          # 실 API
# CHECK API 없이(픽스처 데이터): SPRING_PROFILES_ACTIVE=fixture ANTHROPIC_API_KEY=... ./gradlew bootRun
cd ../frontend && npm install && npm run dev                        # localhost:5173
```

## 테스트

```bash
cd backend && ./gradlew test                                        # 단위 테스트 (MySQL·Redis 필요)
RUN_EVAL=true ANTHROPIC_API_KEY=... ./gradlew test --tests '*EvalRunner'   # 의도 인식 정확도
cd frontend && npm test
```

## 데모 준비

```bash
./scripts/warmup.sh    # 데모 질문 사전 실행 → Redis 캐시 예열 + MySQL check_fallback 스냅샷 적재(장애 폴백)
```

## Admin (수요조사 대시보드)

로그인 인증이 없는 MVP이므로 공유 시크릿(`ADMIN_TOKEN`, 기본 `kopilot-demo`)으로만 보호한다.
- 화면: `http://localhost:5173/#/admin?k=<ADMIN_TOKEN>`
- API: `GET /api/admin/demand/summary` (헤더 `X-Admin-Token` 또는 쿼리 `?token=`)
- 토큰이 틀리면 404를 반환해 엔드포인트 존재 자체를 노출하지 않는다.

## 인증·세션

MVP는 회원가입/로그인이 없다. 브라우저가 발급한 UUID v4를 localStorage에 보관해 익명 세션 식별자로 쓰고,
대화 컨텍스트는 Redis(TTL 2h), 대화 이력은 MySQL `chat_log`에 영속 저장한다.
시연 중 동시 접속자는 세션 ID로 컨텍스트가 격리된다.

## CHECK API

엔드포인트·응답 필드 조사 결과는 리포 루트의 `docs/check-api/`(README.md · specs.json · menu.json) 참조.

## 구조

backend: chat(Spring AI 수동 tool 루프·SSE) / catalog(지표 실행기 6종) / guide(API 명세 인덱스·레시피)
/ demand(수요조사 적재·Admin 집계) / checkapi(CHECK API 클라이언트·Redis 캐시·MySQL 폴백·종목마스터)
/ export(카드 저장·xlsx). frontend: React SPA (+ `#/admin` 대시보드).
지표 추가 = `MetricExecutor` 구현 클래스 1개 + Bean 등록.
```

- [ ] **Step 3: 데모 리허설 (수동 체크리스트 — 스펙 12절 발표 시나리오)**

fixture 프로파일(또는 워밍업된 실 API)로 전체 시나리오를 처음부터 끝까지 실행하고 각 항목을 확인:

1. [ ] 질문 ① "삼성전자랑 코스피, 최근 한 달 수익률 갭" → 카드+차트, 근거 패널 펼쳐 API 링크·원본 수치·공식 시연
2. [ ] 질문 ② "에코 지난주 시세 요약" → 칩 되묻기 → 클릭 한 번에 답변
3. [ ] 질문 ③ "삼성전자 외국인 순매수" → 가이드 카드 (거절 없이 레시피+명세 링크)
4. [ ] 카드에서 xlsx 다운로드 → 열어서 시트 3개·수치 일치 확인
5. [ ] "삼성전자 지금 사야 돼?" → 투자 판단 거절·정보성 전환 확인
6. [ ] `build/eval-report.txt` 의도 인식 정확도 % 수치 확보 (발표 슬라이드용)
7. [ ] 하단 고지 문구 표시 확인
8. [ ] 브라우저 2개(시크릿 창 포함)로 동시 질문 → 세션 격리 확인 (한쪽 맥락이 다른 쪽에 새지 않음)
9. [ ] `#/admin?k=...` 대시보드 → 방금 발생한 가이드 카드 수요가 상위 항목으로 집계되는지 확인
       (발표 멘트: "카탈로그 밖 질문이 곧 지표 확장 우선순위 데이터로 쌓인다")
10. [ ] CHECK API 장애 리허설: `checkapi.base-url`을 잘못된 주소로 바꿔 재기동 →
        warmup으로 적재된 `check_fallback` 스냅샷으로 데모 질문이 여전히 답변되는지 확인

문제 발견 시: tool 오인식은 description·시스템 프롬프트 튜닝 후 EvalRunner 재실행으로 회귀 확인, UI 문제는 해당 컴포넌트 수정.

- [ ] **Step 4: Commit**

```bash
git add -A && git commit -m "chore: demo warmup script with fallback snapshot, readme and rehearsal checklist"
```

---

---

## 태스크 의존성 요약

```
1 → 2 → 3 → 4 → 5 → 6, 7 (병렬 가능)
            5 → 8 → 9 → 10 → 11 → 13
                                12 → 13 → 14
                                11 → 15
                          13 → 16 → 17 → 18 → 19 → 20
```

- CHECK API 실호출 검증 완료(2026-07-22): POST 전용, `{"success":true,"results":[...]}`, 최신→과거 정렬, 수치 문자열, 존재하지 않는 종목은 `results:[]`. 지표 6종에 필요한 데이터가 실데이터로 전부 확인됐다 — 스펙 13절 리스크 #1 해소. 상세는 `docs/check-api/README.md`
- Spring AI 스파이크 완료(2026-07-22): 시그니처 전량 확정. 남은 미검증은 `internalToolExecutionEnabled(false)`의 런타임 동작 1건 — Task 13 Step 5에서 확인
- Task 6·7은 상호 독립(둘 다 Task 5 의존). Task 11·12도 상호 독립이라 병렬 가능
- Task 14(평가셋)·15(Admin API)·16(프론트)은 상호 독립

## 스펙 범위 확인 (MVP 제외 항목 — 구현하지 않음)

MCP 서버 노출 · White-label 위젯 · 과금 · 로그인 인증/계정/권한(익명 세션만 사용) · 차트 PNG 내보내기 · 사용자 정의 지표 등록(버튼은 기록만 — Task 11) · 테마/업종명 종목 자동 확장(되묻기로 처리 — Task 14 평가 케이스 포함) · 해설 토큰 단위 스트리밍(헤더의 구현 결정 2 참고)
