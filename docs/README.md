# Check Kopilot 문서

트레이더가 자연어로 물으면 LLM이 지표 tool을 고르고, **백엔드 Java가 CHECK API 호출과 계산을 수행해** 근거·차트·xlsx가 달린 검증 가능한 답변 카드를 돌려주는 대화형 코파일럿. 코스콤 미니프로젝트(해커톤), 개발 기간 1~2주.

## 문서

| 문서 | 내용 |
|---|---|
| [spec.md](spec.md) | **설계 스펙** — 문제 정의, 지표 카탈로그 6종, 아키텍처, 화면·UX, 가드레일, 데모 시나리오 |
| [plan.md](plan.md) | **구현 계획** — Task 1~20. 각 Task에 파일·인터페이스·Step별 코드 전문 |
| [check-api/](check-api/) | **CHECK API 조사 결과** — 호출 규약, 엔드포인트 776건, F코드 사전 1,841개 |
| [../.github/CONTRIBUTING.md](../.github/CONTRIBUTING.md) | **협업 규칙** — 이슈·라벨·브랜치·커밋·PR 컨벤션 |

## 코드

`check-kopilot/backend` (Spring Boot) · `check-kopilot/frontend` (React) — 실행 방법은 `check-kopilot/README.md`

## 확정된 것 (2026-07-22 기준)

**스택** — Java 21 / Spring Boot 3.5.3 / Spring AI 1.0.0(Claude `claude-opus-4-8`) / MySQL 8 / Redis 7 / React 19 + Vite + Recharts

**핵심 원칙 (변경 금지)**
- 모든 수치 계산은 백엔드 Java가 수행한다. LLM은 tool 선택·파라미터 추출·해설만 담당한다
- 지표 답변에는 호출 API·원본 수치·공식·중간 계산값을 반드시 공개한다
- 투자 판단·권유·전망을 생성하지 않는다
- 화면 하단 고지 문구 고정: "본 자료는 AI가 시장 데이터 기반으로 생성한 정보성 자료이며 투자권유가 아닙니다."

**스펙과 다르게 결정한 것** — 자세한 근거는 [plan.md](plan.md) 상단
- Spring AI를 쓰되 자동 tool 실행을 끄고 수동 루프를 돌린다(tool 실행 중 SSE로 카드를 밀어내야 하므로)
- DB는 PostgreSQL이 아니라 MySQL 8, 세션·캐시는 Redis 7 (기획서 "사용 기술" 표를 따름)
- 로그인 인증은 MVP 제외. 브라우저 발급 UUID로 익명 세션만 격리한다

**CHECK API 규약** — 자세한 내용은 [check-api/README.md](check-api/README.md)
- **POST 전용**, 인증정보(`cust_id`/`auth_key`)는 헤더가 아니라 payload
- HTTP status는 실패해도 항상 200 → `success` 필드로 판정
- 존재하지 않는 종목은 에러가 아니라 빈 `results`
- 시계열은 **최신→과거 내림차순**, 수치는 문자열
- 지표 6종 중 5종이 `/stock/m001/hist_info` 하나로 커버된다 (`F15301`로 ETF 괴리율까지)

## 진행 상황

| Task | 상태 |
|---|---|
| 1. 프로젝트 스캐폴딩 | 완료 |
| 2. CHECK API 클라이언트 | 완료 |
| 3~20 | 진행 예정 |

**미해결 3건**
1. Spring AI `internalToolExecutionEnabled=false`의 런타임 동작 미검증 (유효한 `ANTHROPIC_API_KEY` 필요, Task 13 전까지 무관)
2. `RestCheckApiClient`의 F코드 파싱·정렬 처리에 회귀 테스트 없음 (스모크 확인만)
3. EDU 계정 데이터가 실시장 값과 달라 보임 — 발표 신뢰도 관련, 코스콤 확인 필요
