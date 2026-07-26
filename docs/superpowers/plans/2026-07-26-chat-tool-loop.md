# chat 모듈(Spring AI 수동 tool 루프) 구현 계획

> **For agentic workers:** 이 계획은 task 단위로 실행한다. 각 Step은 체크박스(`- [ ]`)로 추적한다.

**Goal:** 사용자의 자연어 질문이 Claude tool 선택을 거쳐 백엔드 지표 실행기에 도달하고, 카드·되묻기·가이드가 SSE로 흐른 뒤 해설 텍스트로 끝나는 경로를 완성한다.

**Architecture:** Spring AI의 자동 tool 실행을 끄고(`internalToolExecutionEnabled=false`) 애플리케이션이 tool 호출을 직접 디스패치한다. tool 실행 도중 SSE로 카드를 밀어내야 하는데 자동 실행은 그 훅 지점을 주지 않기 때문이다. 디스패치 계층(`ToolDispatcher`)과 LLM 호출부(`ChatService`)를 분리해 둘 다 인프라 없이 단위 테스트한다.

**Tech Stack:** Java 21 · Spring Boot 3.5.3 · Spring AI 1.0.0 (`spring-ai-starter-model-anthropic`) · Jackson · MySQL 8 · Redis 7 · JUnit 5 + AssertJ

## Global Constraints

프로젝트 규칙(`CLAUDE.md`, `docs/spec.md` 5절). 모든 Task에 적용된다.

- **모든 수치 계산은 백엔드 Java가 한다.** LLM은 tool 선택·파라미터 추출·해설 텍스트만 담당한다. LLM 텍스트의 수치를 카드에 쓰지 않는다.
- **지표 답변에는 근거를 공개한다** — 호출 API + 명세 링크, 원본 수치, 공식, 중간 계산값.
- **투자 판단·권유·전망을 생성하지 않는다.**
- Claude 모델 ID는 `claude-opus-4-8`. Spring AI 자동 tool 실행은 **끈 채로** 수동 루프를 돌린다.
- `cust_id` / `auth_key` / API 키는 절대 커밋하지 않는다. 환경변수로만 주입한다.
- 커밋은 Conventional Commits, type은 영문 / 설명은 한국어 명사형·마침표 없음. 본문에 `Refs #<이슈번호>`.
- **AI 도구 트레일러(`Co-Authored-By`, `Claude-Session` 등)는 넣지 않는다.**
- 백엔드 명령은 모두 `check-kopilot/backend` 기준. 테스트: `./gradlew test --tests '*Xxx'`

## 전제

브랜치 `feat/13-chat-tool-loop`는 PR #26(Task 12) · #32(Task 7) · #33(Task 8)이 병합된 `main`에서 분기한다. 따라서 아래가 **이미 존재한다** — 새로 만들지 말 것:

| 심볼 | 위치 | 시그니처 |
|---|---|---|
| `SessionIds` | `chat` | `static String requireValid(String)` |
| `ConversationStore` | `chat` | `List<Message> load(String)` / `void save(String, List<Message>)` / `void clear(String)` |
| `ConversationCodec` | `chat` | `encode` / `decode` / `trimToRecent` |
| `RedisConversationStore` | `chat` | `@Service`, `ConversationStore` 구현 |
| `CatalogService` | `catalog` | `List<MetricExecutor> all()` / `MetricExecutor byName(String)` |
| 실행기 6종 | `catalog` | tool 이름: `return_gap`, `volatility`, `period_summary`, `nav_disparity`, `ma_disparity`, `return_ranking` |
| `ExecutorSupport` | `catalog` | 생성자 `(CheckApiClient, StockResolver, ApiSpecIndex)` — **3-arg** |
| `GuideService` | `guide` | `GuideResult recipeContext(String topic, List<String> keywords)` / `List<ApiSpecEntry> specs(List<String> apiIds)` |
| `GuideService.GuideResult` | `guide` | `record (String topic, List<ApiSpecEntry> matched, List<CatalogLine> catalog, List<String> usedKeywords)` |
| `GuideService.CatalogLine` | `guide` | `record (String apiId, String name, String summary)` |
| `ApiSpecIndex` | `guide` | `static ApiSpecIndex loadFromClasspath()` |
| `FieldDictionary` | `guide` | `static FieldDictionary loadFromClasspath()` |
| `CardStore` | `export` | `void save(String sessionId, MetricResult r)` |
| `TestStocks` | `catalog` (test) | `static StockResolver resolver()` |

**참조 원본:** `docs/plan.md`의 `### Task 10` / `### Task 13` 구간에 이 계획이 재사용할 코드 전문이 있다. 파일이 263KB이므로 `grep -n '^### Task 1[03]' docs/plan.md`로 줄 범위를 찾아 **해당 구간만** 읽을 것. 아래 각 Task에 "plan.md 원문 대비 차이"를 명시했다 — 차이가 없다고 적힌 파일은 원문을 그대로 쓰고, 차이가 적힌 파일은 이 계획의 코드를 쓴다.

## 파일 구조

| 파일 | 책임 |
|---|---|
| `export/CardSink.java` (신규) | chat → export 방향 경계. 카드 저장 계약 하나 |
| `chat/DispatchResult.java` (신규) | 디스패치 산출물: tool_result JSON + 에러 여부 + SSE push |
| `chat/ToolDispatcher.java` (신규) | tool 이름 → 실행기/가이드 라우팅, 예외 → 구조화 결과 변환 |
| `chat/KopilotTools.java` (신규) | 카탈로그 + guide를 스키마 전용 `ToolCallback` 8종으로 변환 |
| `chat/SystemPrompt.java` (신규) | 시스템 프롬프트 렌더링(오늘 날짜 주입) |
| `chat/EventSink.java` (신규) | 이벤트 송출 계약. `ChatService`를 서블릿에서 분리 |
| `chat/SseEmitterSink.java` (신규) | `EventSink` → `SseEmitter` 어댑터 |
| `chat/ChatLogService.java` (신규) | `chat_log` 적재(관측성·평가셋 원천) |
| `chat/ChatService.java` (신규) | 수동 tool 루프 본체 |
| `chat/ChatController.java` (신규) | `POST /api/chat/{sessionId}` → `text/event-stream` |
| `chat/ChatConfig.java` (신규) | `KopilotTools` 빈, 가상 스레드 executor |
| `export/CardStore.java` (수정) | `implements CardSink` 추가 |

---

### Task 0: 대화 컨텍스트 트림의 tool 짝 정합성 수정

PR #26 리뷰에서 확인된 **Task 13의 블로커**다. 이 수정 없이 루프를 올리면 대화가 길어지는 순간 실패한다.

**문제 1 (블로커)** — `ConversationCodec.trimToRecent`가 맹목적 `subList`라서 `AssistantMessage`(tool_use)와 뒤따르는 `ToolResponseMessage`(tool_result)를 쪼갠다. `max-history-turns: 20`에서 tool 라운드가 섞인 대화는 22메시지를 넘는 순간 잘린 히스토리가 **고아 `tool_result`로 시작**한다. Spring AI는 `MessageType.TOOL`을 `tool_result` 콘텐츠 블록으로 보내고, Anthropic은 대응하는 `tool_use`가 없는 `tool_result`를 **HTTP 400으로 거부**한다 → 해당 세션은 TTL 2시간 동안 모든 요청이 실패한다. 같은 뿌리에서, 루프가 중간에 끊겨 `tool_use`로 **끝나는** 히스토리가 저장돼도 다음 턴에 400이 난다.

**문제 2** — `decode`는 null/빈/깨진 입력에 불변 `List.of()`를, 정상 입력엔 가변 `ArrayList`를 반환한다. `trimToRecent`도 `size <= max`면 호출자의 리스트 인스턴스를 그대로 돌려준다. 호출부가 `load()` 결과에 `add`하면 **새 세션 첫 메시지에서 `UnsupportedOperationException`** — 가장 흔한 경로에서 500이다. (이 계획의 `ChatService`는 새 `ArrayList`에 `addAll`하므로 직접 밟지는 않지만, 계약이 일관되지 않은 채로 두면 다음 사람이 밟는다.)

**Files:**
- Modify: `check-kopilot/backend/src/main/java/com/koscom/kopilot/chat/ConversationCodec.java`
- Test: `check-kopilot/backend/src/test/java/com/koscom/kopilot/chat/ConversationCodecTest.java` (기존 파일에 테스트 추가)

**Interfaces:**
- Produces: `trimToRecent`의 계약 강화 — 반환 히스토리는 **항상 `UserMessage`로 시작**하고, 짝 없는 `tool_use`/`tool_result`를 포함하지 않는다. `decode`/`trimToRecent`는 **항상 가변 `ArrayList`**를 반환한다.

- [ ] **Step 1: 실패하는 테스트 추가** — `ConversationCodecTest.java`에 아래 3개를 덧붙인다 (기존 2개는 그대로 둘 것).

```java
    @Test
    void trimNeverStartsWithOrphanToolResult() {
        // user / assistant(tool_use) / tool_result / assistant 4메시지가 한 턴
        List<Message> history = new java.util.ArrayList<>();
        for (int turn = 0; turn < 6; turn++) {
            history.add(new UserMessage("q" + turn));
            history.add(new AssistantMessage("", java.util.Map.of(), List.of(
                    new AssistantMessage.ToolCall("call_" + turn, "function", "return_gap", "{}"))));
            history.add(new ToolResponseMessage(List.of(
                    new ToolResponseMessage.ToolResponse("call_" + turn, "return_gap", "{}"))));
            history.add(new AssistantMessage("a" + turn));
        }

        List<Message> trimmed = codec.trimToRecent(history, 10);

        // 고아 tool_result로 시작하면 Anthropic이 400으로 거부한다 — 세션이 통째로 죽는다
        assertThat(trimmed.get(0)).isInstanceOf(UserMessage.class);
        assertThat(trimmed).hasSizeLessThanOrEqualTo(10);
    }

    @Test
    void trimDropsDanglingToolCallAtTheEnd() {
        // tool_use로 끝나는 히스토리(루프 중단·클라이언트 이탈)를 저장하면 다음 턴에 400이 난다
        List<Message> history = List.of(
                new UserMessage("q"),
                new AssistantMessage("", java.util.Map.of(), List.of(
                        new AssistantMessage.ToolCall("call_1", "function", "return_gap", "{}"))));

        List<Message> trimmed = codec.trimToRecent(history, 20);

        assertThat(trimmed).hasSize(1);
        assertThat(trimmed.get(0)).isInstanceOf(UserMessage.class);
    }

    @Test
    void decodeAlwaysReturnsMutableList() {
        // 호출부가 load() 결과에 add 하는 것은 자연스러운 사용법이다 — 첫 턴에 터지면 안 된다
        assertThatCode(() -> codec.decode(null).add(new UserMessage("x"))).doesNotThrowAnyException();
        assertThatCode(() -> codec.decode("").add(new UserMessage("x"))).doesNotThrowAnyException();
        assertThatCode(() -> codec.decode("깨진 json").add(new UserMessage("x"))).doesNotThrowAnyException();
        assertThatCode(() -> codec.decode(codec.encode(List.of(new UserMessage("q"))))
                .add(new UserMessage("x"))).doesNotThrowAnyException();
        assertThatCode(() -> codec.trimToRecent(new java.util.ArrayList<>(List.of(new UserMessage("q"))), 20)
                .add(new UserMessage("x"))).doesNotThrowAnyException();
    }
```

`ToolResponseMessage` import가 기존 테스트에 이미 있는지 확인하고, 없으면 추가한다.

- [ ] **Step 2: 테스트 실패 확인**

```bash
cd check-kopilot/backend && ./gradlew test --tests '*ConversationCodecTest'
```

Expected: `trimNeverStartsWithOrphanToolResult`가 첫 원소로 `ToolResponseMessage`를 받아 FAIL, `trimDropsDanglingToolCallAtTheEnd`가 size 2로 FAIL, `decodeAlwaysReturnsMutableList`가 `UnsupportedOperationException`으로 FAIL.

- [ ] **Step 3: 구현** — `ConversationCodec`의 `trimToRecent`를 짝 인식 방식으로 바꾸고, 반환을 항상 가변 리스트로 통일한다.

```java
    /**
     * 최근 max개 메시지만 남기되 tool 호출 짝을 깨뜨리지 않는다.
     * Anthropic은 대응하는 tool_use 없는 tool_result(또는 그 반대)를 400으로 거부하므로,
     * "히스토리는 항상 UserMessage로 시작하고 tool_use로 끝나지 않는다"를 불변식으로 강제한다.
     */
    public List<Message> trimToRecent(List<Message> messages, int max) {
        if (messages == null || messages.isEmpty() || max <= 0) {
            return new ArrayList<>();
        }

        List<Message> window = messages.size() <= max
                ? new ArrayList<>(messages)
                : new ArrayList<>(messages.subList(messages.size() - max, messages.size()));

        // 앞: 첫 UserMessage 전까지 버린다 (고아 tool_result·assistant 제거)
        int start = 0;
        while (start < window.size() && !(window.get(start) instanceof UserMessage)) {
            start++;
        }
        List<Message> trimmed = new ArrayList<>(window.subList(start, window.size()));

        // 뒤: 응답 없는 tool_use로 끝나면 그 assistant까지 버린다
        while (!trimmed.isEmpty()) {
            Message last = trimmed.get(trimmed.size() - 1);
            if (last instanceof AssistantMessage assistant && assistant.hasToolCalls()) {
                trimmed.remove(trimmed.size() - 1);
                continue;
            }
            break;
        }
        return trimmed;
    }
```

`decode`의 세 군데 `return List.of();`를 `return new ArrayList<>();`로 바꾼다 (null·blank·파싱 실패).

- [ ] **Step 4: 테스트 통과 확인**

```bash
cd check-kopilot/backend && ./gradlew test --tests '*ConversationCodecTest'
```

Expected: BUILD SUCCESSFUL, 5 tests passed (기존 2 + 신규 3).

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "fix(backend): 대화 컨텍스트 트림이 tool 호출 짝을 깨뜨리는 문제 수정

잘린 히스토리가 고아 tool_result로 시작하거나 응답 없는 tool_use로 끝나면
Anthropic이 400으로 거부해 세션이 TTL 동안 복구되지 않는다

Refs #13"
```

---

### Task 1: CardSink + ToolDispatcher

Claude 호출부와 분리된, 인프라 없이 테스트 가능한 디스패치 계층.

**Files:**
- Create: `check-kopilot/backend/src/main/java/com/koscom/kopilot/export/CardSink.java`
- Create: `check-kopilot/backend/src/main/java/com/koscom/kopilot/chat/DispatchResult.java`
- Create: `check-kopilot/backend/src/main/java/com/koscom/kopilot/chat/ToolDispatcher.java`
- Modify: `check-kopilot/backend/src/main/java/com/koscom/kopilot/export/CardStore.java` (클래스 선언에 `implements CardSink`)
- Test: `check-kopilot/backend/src/test/java/com/koscom/kopilot/chat/ToolDispatcherTest.java`

**Interfaces:**
- Consumes: `CatalogService`, `GuideService`, `ApiSpecIndex`, `FieldDictionary`, `MetricResult`, `AmbiguousStockException`, `StockNotFoundException`, `MetricException`, `CheckApiException`
- Produces:
  - `interface CardSink { void save(String sessionId, MetricResult r); }`
  - `record DispatchResult(String toolResultJson, boolean isError, SsePush push)`, 중첩 `record SsePush(String event, String dataJson)` — `push`는 null 가능
  - `class ToolDispatcher { DispatchResult dispatch(String sessionId, String toolName, JsonNode args); }`, 상수 `EXPLAIN_RECIPE = "explain_recipe"`, `GET_API_SPEC = "get_api_spec"`

**plan.md 원문 대비 차이:** 없음. `docs/plan.md` Task 10의 Step 1·Step 3 코드를 그대로 쓴다.

- [ ] **Step 1: 실패하는 테스트 작성** — `docs/plan.md` Task 10 Step 1의 `ToolDispatcherTest.java` 전문을 그대로 생성한다. 5개 테스트: 지표 성공(카드 저장 + `card` 이벤트 + 컴팩트 tool_result), 모호 종목(`clarify`), 검증 실패(`isError=true` + `PERIOD_INVERTED`), `explain_recipe`(`guide`), `get_api_spec`(이벤트 없음).

- [ ] **Step 2: 테스트 실패 확인**

```bash
cd check-kopilot/backend && ./gradlew test --tests '*ToolDispatcherTest'
```

Expected: 컴파일 에러로 FAIL (`ToolDispatcher` 심볼 없음).

- [ ] **Step 3: 구현** — `docs/plan.md` Task 10 Step 3의 `CardSink.java` / `DispatchResult.java` / `ToolDispatcher.java` 전문을 그대로 생성하고, `CardStore` 클래스 선언에 `implements CardSink`를 추가한다(메서드 시그니처가 이미 같으므로 본문 변경 없음).

계약 확인 — 아래 매핑이 코드와 일치해야 한다:

| 상황 | tool_result | SSE | isError |
|---|---|---|---|
| 지표 정상 | `{"status":"ok","cardId","title","period","headline":[…]}` (**rawData 미포함** — 토큰 절약) | `card` (MetricResult 전문) | false |
| `AmbiguousStockException` | `{"status":"ambiguous","query","candidates"}` | `clarify` | false |
| `StockNotFoundException` | `{"status":"not_found","query","suggestions"}` | — | false |
| `MetricException` | `{"status":"error","code","message"}` | — | **true** |
| `CheckApiException` | `{"status":"error","code":"CHECK_API_ERROR","message"}` | — | **true** |
| `explain_recipe` | `{"topic","matched","catalog","usedKeywords"}` | `guide` (동일 JSON) | false |
| `get_api_spec` | `ApiSpecEntry` 배열 | — | false |

모호·미발견이 `isError=false`인 것은 의도적이다 — 되묻기를 유도해야 하므로 에러가 아니다.

- [ ] **Step 4: 테스트 통과 확인**

```bash
cd check-kopilot/backend && ./gradlew test --tests '*ToolDispatcherTest'
```

Expected: BUILD SUCCESSFUL, 5 tests passed.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat(backend): tool 디스패처 추가

지표·가이드 tool 호출을 실행기로 라우팅하고 예외를 구조화 결과와 SSE 이벤트로 변환

Refs #13"
```

---

### Task 2: KopilotTools + SystemPrompt

tool 스키마 8종 생성과 시스템 프롬프트. LLM 호출은 아직 없다.

**Files:**
- Create: `check-kopilot/backend/src/main/java/com/koscom/kopilot/chat/KopilotTools.java`
- Create: `check-kopilot/backend/src/main/java/com/koscom/kopilot/chat/SystemPrompt.java`
- Test: `check-kopilot/backend/src/test/java/com/koscom/kopilot/chat/KopilotToolsTest.java`

**Interfaces:**
- Consumes: `CatalogService`, `ToolDispatcher.EXPLAIN_RECIPE`, `ToolDispatcher.GET_API_SPEC`
- Produces:
  - `class KopilotTools { KopilotTools(CatalogService catalog); List<ToolCallback> build(); }`
  - `final class SystemPrompt { static String render(LocalDate today); }`

**plan.md 원문 대비 차이:** 없음. `docs/plan.md` Task 13 Step 1·Step 3의 `KopilotToolsTest.java` / `KopilotTools.java` / `SystemPrompt.java` 전문을 그대로 쓴다.

Spring AI 시그니처는 스파이크로 확정됐다(추측 금지):

```
ToolCallback:                  getToolDefinition() / call(String) / call(String, ToolContext)
ToolDefinition:                name() / description() / inputSchema() / static builder()
DefaultToolDefinition.Builder: name(String) / description(String) / inputSchema(String) / build()
ToolContext 패키지:             org.springframework.ai.chat.model.ToolContext
```

- [ ] **Step 1: 실패하는 테스트 작성** — `docs/plan.md` Task 13 Step 1의 `KopilotToolsTest.java` 전문. 테스트 2개: tool 8종의 이름·JSON 스키마, `call()`이 `IllegalStateException`을 던지는지.

- [ ] **Step 2: 테스트 실패 확인**

```bash
cd check-kopilot/backend && ./gradlew test --tests '*KopilotToolsTest'
```

Expected: 컴파일 에러로 FAIL.

- [ ] **Step 3: 구현** — `docs/plan.md` Task 13 Step 3의 `KopilotTools.java`(`SchemaOnlyToolCallback` 중첩 클래스 포함)와 `SystemPrompt.java` 전문.

`SchemaOnlyToolCallback.call()`이 예외를 던지는 것은 방어 장치다: 자동 tool 실행이 켜지면 조용히 우회되는 대신 즉시 터진다. `call(String)`과 `call(String, ToolContext)` **둘 다** 구현한다.

- [ ] **Step 4: 테스트 통과 확인**

```bash
cd check-kopilot/backend && ./gradlew test --tests '*KopilotToolsTest'
```

Expected: BUILD SUCCESSFUL, 2 tests passed.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat(backend): tool 스키마 8종과 시스템 프롬프트 추가

Refs #13"
```

---

### Task 3: EventSink + ChatService 수동 루프

이 계획의 핵심. `docs/plan.md` Task 13 원문에서 **의도적으로 벗어나는 유일한 Task**다.

**차이 ①** — 원문 `ChatService.handle(String, String, SseEmitter)`는 서블릿 타입에 직접 의존해 루프를 테스트할 수 없다. `EventSink` 인터페이스를 끼워 `ChatService`가 서블릿을 모르게 한다.
**차이 ②** — 원문은 루프를 수동 curl로만 검증한다. `ChatModel`이 인터페이스이므로 스크립트된 응답을 반환하는 테스트 더블로 API 키 없이 루프를 검증한다.

**Files:**
- Create: `check-kopilot/backend/src/main/java/com/koscom/kopilot/chat/EventSink.java`
- Create: `check-kopilot/backend/src/main/java/com/koscom/kopilot/chat/ChatLogService.java`
- Create: `check-kopilot/backend/src/main/java/com/koscom/kopilot/chat/ChatService.java`
- Test: `check-kopilot/backend/src/test/java/com/koscom/kopilot/chat/ChatServiceTest.java`

**Interfaces:**
- Consumes: `ChatModel`(Spring AI), `KopilotTools`, `ToolDispatcher`, `ConversationStore`, `ChatLogService`
- Produces:
  - `interface EventSink { void send(String event, String dataJson); }`
  - `class ChatLogService { ChatLogService(JdbcTemplate jdbc); void log(String sessionId, String role, String toolName, String content); }`
  - `class ChatService { ChatService(ChatModel, KopilotTools, ToolDispatcher, ConversationStore, ChatLogService); void handle(String sessionId, String userMessage, EventSink sink); }`

- [ ] **Step 1: `EventSink` 작성**

```java
package com.koscom.kopilot.chat;

/**
 * 채팅 진행 상황을 프론트로 밀어내는 통로.
 * ChatService가 서블릿 SseEmitter를 직접 알지 않게 하는 경계 — 루프 로직을 인프라 없이 테스트하기 위함이다.
 * 송출 실패는 대화 자체를 중단시킬 이유가 아니므로 구현체가 삼킨다.
 */
public interface EventSink {
    void send(String event, String dataJson);
}
```

- [ ] **Step 2: `ChatLogService` 작성** — `docs/plan.md` Task 13 Step 3의 `ChatLogService.java` 전문 그대로.

- [ ] **Step 3: 실패하는 테스트 작성** — `ChatServiceTest.java`

```java
package com.koscom.kopilot.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.koscom.kopilot.catalog.*;
import com.koscom.kopilot.checkapi.FixtureCheckApiClient;
import com.koscom.kopilot.domain.MetricResult;
import com.koscom.kopilot.export.CardSink;
import com.koscom.kopilot.guide.ApiSpecIndex;
import com.koscom.kopilot.guide.FieldDictionary;
import com.koscom.kopilot.guide.GuideService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class ChatServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final List<String> events = new ArrayList<>();
    private final List<Prompt> seenPrompts = new ArrayList<>();
    private final List<Message> savedContext = new ArrayList<>();

    /** 스크립트된 응답을 순서대로 돌려주는 ChatModel 테스트 더블. 실 LLM 없이 루프를 검증한다. */
    private class ScriptedChatModel implements ChatModel {
        private final Deque<AssistantMessage> script = new ArrayDeque<>();

        ScriptedChatModel(AssistantMessage... responses) {
            for (AssistantMessage m : responses) script.add(m);
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            seenPrompts.add(prompt);
            AssistantMessage next = script.poll();
            if (next == null) throw new AssertionError("스크립트보다 많이 호출됨");
            return new ChatResponse(List.of(new Generation(next, ChatGenerationMetadata.NULL)));
        }
    }

    private final EventSink sink = (event, dataJson) -> events.add(event);

    private final ConversationStore conversations = new ConversationStore() {
        @Override public List<Message> load(String sessionId) { return List.of(); }
        @Override public void save(String sessionId, List<Message> messages) {
            savedContext.clear();
            savedContext.addAll(messages);
        }
        @Override public void clear(String sessionId) { }
    };

    private final List<String> logged = new ArrayList<>();
    private final ChatLogService logs = new ChatLogService(null) {
        @Override public void log(String sessionId, String role, String toolName, String content) {
            logged.add(role + ":" + toolName);
        }
    };

    private ToolDispatcher dispatcher() {
        ApiSpecIndex index = ApiSpecIndex.loadFromClasspath();
        ExecutorSupport support =
                new ExecutorSupport(new FixtureCheckApiClient(), TestStocks.resolver(), index);
        CatalogService catalog = new CatalogService(List.of(new ReturnGapExecutor(support)));
        CardSink cards = (sessionId, r) -> { };
        return new ToolDispatcher(catalog,
                new GuideService(index, FieldDictionary.loadFromClasspath()), cards);
    }

    private KopilotTools tools() {
        ApiSpecIndex index = ApiSpecIndex.loadFromClasspath();
        ExecutorSupport support =
                new ExecutorSupport(new FixtureCheckApiClient(), TestStocks.resolver(), index);
        return new KopilotTools(new CatalogService(List.of(new ReturnGapExecutor(support))));
    }

    private static AssistantMessage toolCall(String id, String name, String args) {
        return new AssistantMessage("", Map.of(),
                List.of(new AssistantMessage.ToolCall(id, "function", name, args)));
    }

    @Test
    void dispatchesToolCall_thenEmitsCardTextDone() {
        ChatModel model = new ScriptedChatModel(
                toolCall("call_1", "return_gap",
                        "{\"target_a\":\"삼성전자\",\"target_b\":\"코스피\","
                        + "\"from\":\"2026-07-13\",\"to\":\"2026-07-17\"}"),
                new AssistantMessage("삼성전자가 코스피를 앞섰습니다."));

        new ChatService(model, tools(), dispatcher(), conversations, logs)
                .handle("sess-1", "삼성전자랑 코스피 수익률 갭", sink);

        assertThat(events).containsExactly("card", "text", "done");
    }

    @Test
    void feedsToolResultBackWithMatchingToolUseId() {
        ChatModel model = new ScriptedChatModel(
                toolCall("call_42", "return_gap",
                        "{\"target_a\":\"삼성전자\",\"target_b\":\"코스피\","
                        + "\"from\":\"2026-07-13\",\"to\":\"2026-07-17\"}"),
                new AssistantMessage("해설"));

        new ChatService(model, tools(), dispatcher(), conversations, logs)
                .handle("sess-1", "질문", sink);

        // 2회차 호출 프롬프트에 tool_use id가 그대로 실린 ToolResponseMessage가 있어야 한다.
        // Anthropic은 tool_use/tool_result의 id 불일치를 400으로 거부한다.
        List<Message> second = seenPrompts.get(1).getInstructions();
        ToolResponseMessage toolMsg = second.stream()
                .filter(ToolResponseMessage.class::isInstance)
                .map(ToolResponseMessage.class::cast)
                .findFirst().orElseThrow();
        assertThat(toolMsg.getResponses().get(0).id()).isEqualTo("call_42");
        assertThat(toolMsg.getResponses().get(0).name()).isEqualTo("return_gap");
        assertThat(toolMsg.getResponses().get(0).responseData()).contains("\"status\":\"ok\"");
    }

    @Test
    void noToolCall_emitsTextAndDoneOnly() {
        ChatModel model = new ScriptedChatModel(
                new AssistantMessage("투자 판단은 제공하지 않습니다. 대신 수익률·변동성을 확인하실 수 있습니다."));

        new ChatService(model, tools(), dispatcher(), conversations, logs)
                .handle("sess-1", "삼성전자 사야 돼?", sink);

        assertThat(events).containsExactly("text", "done");
    }

    @Test
    void validationFailure_doesNotBreakStream_returnsErrorToModel() {
        ChatModel model = new ScriptedChatModel(
                toolCall("call_1", "return_gap",
                        "{\"target_a\":\"삼성전자\",\"target_b\":\"코스피\","
                        + "\"from\":\"2026-07-17\",\"to\":\"2026-07-13\"}"),   // 기간 역전
                new AssistantMessage("기간이 뒤집혀 있습니다. 시작일을 다시 알려주세요."));

        new ChatService(model, tools(), dispatcher(), conversations, logs)
                .handle("sess-1", "질문", sink);

        // 카드 이벤트 없이, error 이벤트로 스트림을 끊지도 않고, 되묻기 텍스트로 끝난다
        assertThat(events).containsExactly("text", "done");
        ToolResponseMessage toolMsg = seenPrompts.get(1).getInstructions().stream()
                .filter(ToolResponseMessage.class::isInstance)
                .map(ToolResponseMessage.class::cast)
                .findFirst().orElseThrow();
        assertThat(toolMsg.getResponses().get(0).responseData()).contains("PERIOD_INVERTED");
    }

    @Test
    void stopsAtIterationLimit_insteadOfLoopingForever() {
        AssistantMessage[] endless = new AssistantMessage[8];
        for (int i = 0; i < endless.length; i++) {
            endless[i] = toolCall("call_" + i, "return_gap",
                    "{\"target_a\":\"삼성전자\",\"target_b\":\"코스피\","
                    + "\"from\":\"2026-07-13\",\"to\":\"2026-07-17\"}");
        }
        ChatModel model = new ScriptedChatModel(endless);

        new ChatService(model, tools(), dispatcher(), conversations, logs)
                .handle("sess-1", "질문", sink);

        // 스크립트가 8개뿐이므로 9번째 호출이 일어나면 AssertionError로 터진다 = 상한이 지켜졌다
        assertThat(seenPrompts).hasSize(8);
        assertThat(events).endsWith("done");
    }

    @Test
    void savesContextWithoutSystemMessage() {
        ChatModel model = new ScriptedChatModel(new AssistantMessage("답변"));

        new ChatService(model, tools(), dispatcher(), conversations, logs)
                .handle("sess-1", "질문", sink);

        // 시스템 프롬프트는 매 턴 새로 만든다(오늘 날짜가 바뀌므로) — 저장하면 중복 누적된다
        assertThat(savedContext).noneMatch(m ->
                m instanceof org.springframework.ai.chat.messages.SystemMessage);
        assertThat(savedContext).hasSize(2);   // user + assistant
    }
}
```

- [ ] **Step 4: 테스트 실패 확인**

```bash
cd check-kopilot/backend && ./gradlew test --tests '*ChatServiceTest'
```

Expected: 컴파일 에러로 FAIL (`ChatService` 심볼 없음).

- [ ] **Step 5: `ChatService` 구현**

`docs/plan.md` Task 13 Step 3의 `ChatService.java`를 기반으로 하되 **세 곳을 바꾼다**:

1. `handle`의 3번째 파라미터 타입 `SseEmitter` → `EventSink`, `runTool`도 동일
2. `send(...)` private 메서드 제거 → `sink.send(event, dataJson)` 직접 호출 (`IOException` 없음)
3. `catch` 블록의 `emitter.completeWithError(e)` → `sink.send("error", …)` 후 반환 (스트림 종료는 호출자 책임)

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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Spring AI 수동 tool 루프.
 * 자동 tool 실행을 끄는 이유: tool 실행 도중 SSE로 카드를 밀어내야 하는데 자동 실행은 그 훅 지점을 주지 않는다.
 */
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

    public void handle(String sessionId, String userMessage, EventSink sink) {
        try {
            logs.log(sessionId, "user", null, userMessage);

            // 대화 컨텍스트: Redis에서 복원 → 이번 턴 진행 → 종료 시 통째로 저장
            List<Message> messages = new ArrayList<>();
            messages.add(new SystemMessage(SystemPrompt.render(LocalDate.now())));
            messages.addAll(conversations.load(sessionId));
            messages.add(new UserMessage(userMessage));

            // 핵심: 자동 tool 실행 OFF — tool 호출은 아래 루프가 직접 디스패치한다.
            // yml 병합에 기대지 않도록 모델·토큰도 빌더에 직접 지정한다.
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
                    DispatchResult r = runTool(sessionId, call.name(), call.arguments(), sink);
                    toolResponses.add(new ToolResponseMessage.ToolResponse(
                            call.id(), call.name(), r.toolResultJson()));
                }
                messages.add(new ToolResponseMessage(toolResponses));
            }

            conversations.save(sessionId, messages.subList(1, messages.size()));  // system 제외
            logs.log(sessionId, "assistant", null, finalText);
            sink.send("text", mapper.createObjectNode().put("text", finalText).toString());
            sink.send("done", "{}");
        } catch (Exception e) {
            logs.log(sessionId, "error", null, String.valueOf(e));
            sink.send("error", mapper.createObjectNode()
                    .put("message", "요청 처리에 실패했습니다. 다시 시도해 주세요.").toString());
        }
    }

    /** tool 실행 실패는 스트림을 끊지 않는다 — 구조화 에러로 되돌려 LLM이 자연어로 되묻게 한다. */
    private DispatchResult runTool(String sessionId, String name, String argumentsJson, EventSink sink) {
        try {
            String raw = (argumentsJson == null || argumentsJson.isBlank()) ? "{}" : argumentsJson;
            JsonNode args = mapper.readTree(raw);
            logs.log(sessionId, "tool_call", name, args.toString());
            DispatchResult r = dispatcher.dispatch(sessionId, name, args);
            logs.log(sessionId, "tool_result", name, r.toolResultJson());
            if (r.push() != null) sink.send(r.push().event(), r.push().dataJson());
            return r;
        } catch (Exception e) {
            logs.log(sessionId, "error", name, String.valueOf(e));
            return new DispatchResult(
                    "{\"status\":\"error\",\"code\":\"INTERNAL\",\"message\":\"tool 실행 실패\"}",
                    true, null);
        }
    }
}
```

- [ ] **Step 6: 테스트 통과 확인**

```bash
cd check-kopilot/backend && ./gradlew test --tests '*ChatServiceTest'
```

Expected: BUILD SUCCESSFUL, 6 tests passed.

`ChatLogService`를 익명 서브클래스로 스텁하려면 `log`가 재정의 가능해야 한다(`final` 금지) — 테스트가 컴파일되지 않으면 여기를 확인할 것.

- [ ] **Step 7: Commit**

```bash
git add -A && git commit -m "feat(backend): Spring AI 수동 tool 루프와 대화 로깅 추가

EventSink로 SSE 결합을 분리해 스텁 ChatModel로 루프 전체를 단위 테스트

Refs #13"
```

---

### Task 4: SSE 엔드포인트 배선

**Files:**
- Create: `check-kopilot/backend/src/main/java/com/koscom/kopilot/chat/SseEmitterSink.java`
- Create: `check-kopilot/backend/src/main/java/com/koscom/kopilot/chat/ChatController.java`
- Create: `check-kopilot/backend/src/main/java/com/koscom/kopilot/chat/ChatConfig.java`

**Interfaces:**
- Consumes: `ChatService`, `EventSink`, `SessionIds`, `CatalogService`
- Produces: `POST /api/chat/{sessionId}` → `text/event-stream`. 이벤트 순서 (`card`|`clarify`|`guide`)* → `text` → `done`, 실패 시 `error`. **`docs/api.md` 계약 그대로 — 프론트 무변경.**

- [ ] **Step 1: `SseEmitterSink` 작성**

```java
package com.koscom.kopilot.chat;

import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * EventSink → 서블릿 SSE 어댑터.
 * 송출 실패(클라이언트 이탈 등)는 삼킨다 — 이미 끊긴 연결에 예외를 던져봐야 대화만 중단된다.
 */
public class SseEmitterSink implements EventSink {

    private final SseEmitter emitter;

    public SseEmitterSink(SseEmitter emitter) {
        this.emitter = emitter;
    }

    @Override
    public void send(String event, String dataJson) {
        try {
            emitter.send(SseEmitter.event().name(event)
                    .data(dataJson, MediaType.APPLICATION_JSON));
        } catch (Exception ignored) {
            // 연결이 이미 닫힌 경우
        }
    }
}
```

- [ ] **Step 2: `ChatConfig` 작성** — `docs/plan.md` Task 13 Step 3의 `ChatConfig.java` 전문 그대로 (`KopilotTools` 빈 + 가상 스레드 executor).

- [ ] **Step 3: `ChatController` 작성**

`docs/plan.md` Task 13 Step 3의 `ChatController.java`를 기반으로 하되, `ChatService.handle`이 `EventSink`를 받으므로 어댑터를 끼우고 스트림 종료를 컨트롤러가 책임진다:

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
        chatExecutor.submit(() -> {
            try {
                chatService.handle(safeSessionId, message, new SseEmitterSink(emitter));
            } finally {
                emitter.complete();   // ChatService는 예외를 삼키므로 여기서 항상 닫는다
            }
        });
        return emitter;
    }
}
```

- [ ] **Step 4: 전체 테스트 + 기동 확인**

```bash
cd check-kopilot/backend && ./gradlew test
SPRING_PROFILES_ACTIVE=fixture ./gradlew bootRun
```

Expected: 테스트 BUILD SUCCESSFUL. 기동 시 빈 주입 에러 없이 `Started KopilotApplication`. (API 키 없이도 기동돼야 한다 — 확인 후 Ctrl+C)

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat(backend): 채팅 SSE 엔드포인트와 빈 배선 추가

Refs #13"
```

---

### Task 5: 실 LLM 연동 검증 + 문서 갱신

단위 테스트로 덮을 수 없는 것: **Spring AI가 tool 호출을 자동 실행하지 않고 되돌려주는지.** 이것만 실 API 키로 확인한다.

**Files:**
- Modify: `docs/README.md` (진행 상황 표, 미해결 이슈 1번)
- Modify: `docs/api.md` (구현 상태가 표기돼 있다면 갱신 — 엔드포인트/스키마 변경은 없다)

- [ ] **Step 1: fixture 프로파일로 기동**

```bash
cd check-kopilot && docker compose up -d
cd backend && SPRING_PROFILES_ACTIVE=fixture ANTHROPIC_API_KEY=sk-ant-... ./gradlew bootRun
```

- [ ] **Step 2: 지표 시나리오** (다른 터미널에서)

```bash
curl -N -X POST localhost:8080/api/chat/11111111-1111-4111-8111-111111111111 \
  -H 'Content-Type: application/json' \
  -d '{"message":"삼성전자랑 코스피, 2026-07-13부터 2026-07-17까지 수익률 갭 알려줘"}'
```

Expected 순서: `event: card` + MetricResult JSON(수익률 갭 3.0 포함) → `event: text` + 해설 → `event: done`.

**실패 판정:** 서버 로그에 `IllegalStateException: tool 자동 실행이 활성화되어 있습니다`가 뜨면 `internalToolExecutionEnabled(false)`가 먹지 않은 것이다. 이 경우 Spring AI 1.0.0의 옵션 병합 경로를 확인하고(yml `spring.ai.anthropic.chat.options`와 런타임 옵션 충돌 여부) 결과를 `docs/README.md` 미해결 이슈에 기록할 것.

- [ ] **Step 3: 되묻기 시나리오**

```bash
curl -N -X POST localhost:8080/api/chat/22222222-2222-4222-8222-222222222222 \
  -H 'Content-Type: application/json' \
  -d '{"message":"에코 지난주 시세 요약해줘"}'
```

Expected: `event: clarify` + 후보 종목 → `event: text`(되묻기) → `event: done`.

- [ ] **Step 4: 컴플라이언스 시나리오**

```bash
curl -N -X POST localhost:8080/api/chat/33333333-3333-4333-8333-333333333333 \
  -H 'Content-Type: application/json' \
  -d '{"message":"삼성전자 지금 사야 돼?"}'
```

Expected: tool 호출 없이 `event: text`(투자 판단 거절 + 정보성 전환) → `event: done`. **매수/매도 권유, 목표주가, 전망이 텍스트에 나오면 시스템 프롬프트를 강화할 것.**

- [ ] **Step 5: 가이드 시나리오**

```bash
curl -N -X POST localhost:8080/api/chat/44444444-4444-4444-8444-444444444444 \
  -H 'Content-Type: application/json' \
  -d '{"message":"삼성전자 외국인 순매수 어떻게 뽑아?"}'
```

Expected: `event: guide` + 매칭 API 목록 → `event: text`(레시피 설명) → `event: done`.

- [ ] **Step 6: 멀티턴 컨텍스트 + 로깅 확인**

```bash
curl -N -X POST localhost:8080/api/chat/11111111-1111-4111-8111-111111111111 \
  -H 'Content-Type: application/json' -d '{"message":"같은 기간으로 현대차는?"}'

cd check-kopilot
docker compose exec redis redis-cli KEYS 'kopilot:session:*'
docker compose exec db mysql -ukopilot -pkopilot kopilot \
  -e "SELECT role, tool_name, LEFT(content,60) FROM chat_log ORDER BY id;"
```

Expected: 두 번째 질문이 앞 대화를 이어받아 tool을 호출, Redis에 세션 키 존재, `chat_log`에 user/tool_call/tool_result/assistant 행 누적.

- [ ] **Step 7: 문서 갱신** — `docs/README.md` 진행 상황 표에 Task 10·13 완료를 반영하고, "미해결 3건"의 1번(`internalToolExecutionEnabled=false` 런타임 미검증)을 Step 2 결과로 갱신한다(검증됨 또는 발견된 문제).

- [ ] **Step 8: Commit**

```bash
git add -A && git commit -m "docs: chat 모듈 진행 상황과 tool 실행 검증 결과 반영

Refs #13"
```

---

## 완료 기준

- `./gradlew test` 전체 통과 (신규 13개: ToolDispatcher 5 · KopilotTools 2 · ChatService 6)
- 실 API 키로 시나리오 4종(지표·되묻기·컴플라이언스·가이드) + 멀티턴 통과
- `docs/api.md`의 SSE 계약과 실제 이벤트 이름·순서 일치 — 프론트(PR #34) 무변경
- `docs/README.md` 진행 상황 갱신
