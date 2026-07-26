# 설계: chat 모듈 — Spring AI 수동 tool 루프 + tool 디스패처

- 작성일: 2026-07-26
- 상태: 사용자 승인 (구현 계획 수립 전)
- 대상: `docs/plan.md` Task 10(ToolDispatcher) + Task 13(chat 모듈)
- 관련 스펙: [../../spec.md](../../spec.md) 8절 아키텍처 · 10절 가드레일, [../../api.md](../../api.md) SSE 계약

## 1. 왜 지금 이것인가

지표 실행기와 CHECK API 클라이언트는 이미 있지만 **사용자 질문이 실행기에 도달하는 경로가 없다.** 이 설계가 그 경로 하나를 세로로 뚫는다: 자연어 질문 → LLM tool 선택 → 백엔드 실행 → SSE 카드 → 해설.

### 전제 — 앞단 Task는 병합으로 확보한다

조사 결과 앞단 Task가 전부 팀원 PR로 열려 있었다. 새로 구현하지 않고 병합해 쓴다.

| PR | Task | 이 설계가 쓰는 것 |
|---|---|---|
| #32 | Task 7 실행기 3종 | `nav_disparity`, `ma_disparity`, `return_ranking` |
| #33 | Task 8 guide 모듈 | `GuideService.recipeContext/specs`, `ApiSpecIndex` |
| #26 | Task 12 세션·컨텍스트 | `SessionIds`, `ConversationStore`, `ConversationCodec` |

따라서 이 설계의 구현 범위는 **Task 10 + Task 13 뿐**이고, tool은 지표 6종 + guide 2종 = **8종**이다.

## 2. 구조

```
ChatController ──> ChatService ──> ChatModel (Spring AI Anthropic)
  (SSE·세션검증)       │  수동 루프   └─ KopilotTools: 스키마 전용 ToolCallback 8종
                      ├──> ToolDispatcher ──> CatalogService ──> 실행기 6종
                      │         ├──> GuideService (explain_recipe / get_api_spec)
                      │         └──> CardSink (= CardStore)
                      ├──> ConversationStore (Redis `kopilot:session:{id}`)
                      ├──> ChatLogService (MySQL `chat_log`)
                      └──> EventSink ──> SseEmitter
```

신규 파일은 전부 `com.koscom.kopilot.chat`, 예외로 `export`에 `CardSink` 인터페이스 하나.

### 계획서와 다르게 가는 두 지점

**① `EventSink`로 SSE 결합을 끊는다.** 계획의 `ChatService.handle(sessionId, message, SseEmitter)`는 서블릿 타입에 직접 의존해 루프 로직을 서블릿 없이 테스트할 수 없다. `interface EventSink { void send(String event, String dataJson); }`를 두고 `SseEmitterSink`를 어댑터로 둔다. 추가 코드 15줄로 루프 전체가 단위 테스트 대상이 된다. (Reactor `Flux` 전환은 프론트 계약이 같은데도 스택을 바꾸므로 배제.)

**② 루프를 스텁 `ChatModel`로 단위 테스트한다.** 계획은 루프를 수동 curl로만 검증한다. `ChatModel`은 인터페이스이므로 스크립트된 `ChatResponse`를 순서대로 반환하는 테스트 더블로 API 키 없이 검증할 수 있다. 실 API 키 검증은 유지하되, 거기서만 확인할 것을 **`internalToolExecutionEnabled=false`의 런타임 동작 한 건**으로 좁힌다.

## 3. ToolDispatcher (Task 10)

Claude 호출부와 분리된 **단위 테스트 가능한** 디스패치 계층. tool 이름과 인자를 받아 (a) Claude에 돌려줄 tool_result JSON과 (b) 프론트로 밀어낼 SSE 이벤트를 만든다.

`DispatchResult(String toolResultJson, boolean isError, SsePush push)` / `SsePush(String event, String dataJson)` — `push`는 null 가능.

| 입력·예외 | tool_result | SSE 이벤트 | isError |
|---|---|---|---|
| 지표 정상 | `{"status":"ok","cardId","title","period","headline":[…]}` — 원본 시계열 제외(토큰 절약) | `card` (MetricResult 전문) | false |
| `AmbiguousStockException` | `{"status":"ambiguous","query","candidates"}` | `clarify` | false |
| `StockNotFoundException` | `{"status":"not_found","query","suggestions"}` | — | false |
| `MetricException` | `{"status":"error","code","message"}` | — | **true** |
| `CheckApiException` | `{"status":"error","code":"CHECK_API_ERROR","message"}` | — | **true** |
| `explain_recipe` | `{"topic","matched","catalog","usedKeywords"}` | `guide` (동일 JSON) | false |
| `get_api_spec` | 명세 엔트리 배열 | — | false |

`isError` 구분의 의미: 모호·미발견은 **되묻기를 유도해야 하므로 에러가 아니다.** 검증 실패만 에러로 표시해 Claude가 원인을 설명하게 한다. 어느 경우든 **백엔드는 잘못된 계산 결과를 내지 않는다**(스펙 10절).

`CardSink`를 `export`에 두고 `CardStore implements CardSink` — chat이 export 구현 클래스에 직접 의존하지 않게 하는 경계.

## 4. chat 모듈 (Task 13)

**`KopilotTools`** — `CatalogService.all()`을 돌며 스키마 전용 `ToolCallback`을 만들고 guide tool 2종을 더한다. `call()`은 `IllegalStateException`을 던진다: **자동 tool 실행이 켜지면 조용히 우회되는 대신 즉시 터지게** 하는 장치다.

**`ChatService`** — 수동 루프. 최대 8회 반복.

1. `chat_log`에 user 기록, Redis에서 대화 컨텍스트 복원, 시스템 프롬프트 + 과거 턴 + 이번 질문으로 메시지 구성
2. `ChatModel.call()` → `AssistantMessage`
3. tool 호출이 없으면 종료. 있으면 각 호출을 `ToolDispatcher`로 디스패치하고, `push`가 있으면 **즉시 SSE로 밀어낸 뒤** `ToolResponseMessage`로 재주입 (tool_use id 일치 필수)
4. 종료 시 컨텍스트 저장(system 메시지 제외), `text` → `done` 이벤트

옵션은 yml 병합에 기대지 않고 빌더에 직접 지정한다: `.model("claude-opus-4-8").maxTokens(4096).internalToolExecutionEnabled(false)`.

**`SystemPrompt.render(today)`** — 계획서 원문. 계산 금지(수치는 tool 결과만 인용), 상대 기간의 ISO 변환, 되묻기 규칙, 가이드 모드, **컴플라이언스(투자 판단·권유·전망 생성 금지)**. 오늘 날짜를 주입해 "최근 한 달"을 해석하게 한다.

**`ChatController`** — `POST /api/chat/{sessionId}` → `text/event-stream`. `SessionIds.requireValid`로 형식 검증 후 가상 스레드 executor에 위임, 타임아웃 180초.

**관측성** — `ChatLogService`가 user / tool_call / tool_result / assistant / error를 전부 `chat_log`에 적재한다. 디버깅이자 Task 14 평가셋의 원천 데이터.

## 5. 에러 처리

- **디스패치 실패**(종목 모호, 검증 실패, CHECK API 장애)는 스트림을 끊지 않는다. tool_result로 되돌려 Claude가 자연어로 되묻거나 설명하게 한다.
- **루프 자체 실패**(LLM 장애·타임아웃·직렬화 실패)만 `error` 이벤트 후 스트림 종료. 진행 중 tool 결과는 폐기(스펙 10절).
- **Redis 장애**는 삼킨다 — 맥락 없이 단발 응답으로 계속 동작한다. 영속 이력은 `chat_log`가 가지므로 서비스 중단이 아니다.

SSE 이벤트 순서 계약은 `docs/api.md` 그대로 유지한다: (`card`|`clarify`|`guide`)* → `text` → `done`, 실패 시 `error`. **프론트(PR #34) 무변경.**

## 6. 테스트

| 대상 | 방법 | 인프라 |
|---|---|---|
| `ToolDispatcher` | 픽스처 기반 5케이스 — 카드 저장·이벤트, 모호, 검증실패, 레시피, 명세조회 | 불필요 |
| `KopilotTools` | tool 8종 이름·JSON 스키마, 자동 실행 거부 | 불필요 |
| `ChatService` | **스텁 `ChatModel`** — tool 호출→디스패치→재주입→최종 텍스트, id 매칭, 이벤트 순서, 반복 상한, 디스패치 예외 격리 | 불필요 |
| 실 LLM 연동 | fixture 프로파일 + 실 `ANTHROPIC_API_KEY`로 curl 3종(지표/되묻기/투자판단 거절) + 멀티턴 | docker compose |

실 LLM 검증에서 **최우선 확인 1건**: tool 호출이 자동 실행되지 않고 `getToolCalls()`로 되돌아오는가. `SchemaOnlyToolCallback`이 `IllegalStateException`을 던지면 자동 실행이 켜진 것이다.

## 7. 범위 밖

- 해설 텍스트의 **토큰 단위 스트리밍** — 현재 계약은 `text` 이벤트 1건으로 전문 전송. 스트리밍 전환은 `card` 이벤트 순서 보장과 얽히므로 별도 과제.
- Task 11 수요조사 적재, Task 14 평가셋, Task 15 Admin API.
- 프론트 변경 — SSE 계약이 동일하므로 없다.
