# CHECK Kopilot — API 명세서

> **이 문서의 목적**: 백엔드·프론트·LLM 작업(사람과 코드 에이전트 모두)이 **동일한 API 계약**을 참조하도록 통일하기 위한 정본(正本)입니다.
> 새 엔드포인트를 추가하거나 응답 스키마를 바꾸면 **이 문서를 같은 PR에서 갱신**하세요.
> 복잡한 응답 타입(`MetricResult` 등)의 **최종 원천은 코드**(`com.koscom.kopilot.domain.MetricResult`)이며, 이 문서는 그 요약입니다.

## 공통 사항

- **Base URL**: `http://localhost:8080` (개발) — 배포 시 교체
- **인증**: 일반 API는 인증 없음(익명 세션, `sessionId`는 프론트가 localStorage에 생성한 UUID). **Admin API만** `X-Admin-Token` 헤더(또는 `?token=` 쿼리)
- **에러 응답 형식**: 공통 에러 envelope는 미확정. 아래 에러 예시는 제안 형태이며, 확정 시 통일한다.
- **통신 방식**: 이 API는 **SSE와 REST를 함께** 씁니다 — 실시간으로 여러 조각을 밀어내는 **채팅만 SSE**, 나머지는 일반 **REST**(JSON/파일).

## 엔드포인트 개요

| # | Method | Path | 통신 방식 | 설명 |
| --- | --- | --- | --- | --- |
| 1 | POST | `/api/chat/{sessionId}` | **SSE** (`text/event-stream`) | 자연어 질문 → 카드/되묻기/가이드 스트리밍 |
| 2 | DELETE | `/api/chat/{sessionId}` | **REST** | 새 대화 — 서버 대화 컨텍스트 폐기 |
| 3 | GET | `/api/cards/{cardId}/xlsx` | **REST** (파일) | 카드 3시트 xlsx 다운로드 |
| 4 | POST | `/api/catalog-requests` | **REST** (JSON) | 카탈로그 추가요청 적재 |
| 5 | GET | `/api/admin/demand/summary` | **REST** (JSON) | 관리자 수요 요약 |
| 6 | GET | `/api/admin/stats` | **REST** (JSON) | 관리자 통계 |
| 7 | GET | `/api/catalog` | **REST** (JSON) | 지표 카탈로그 조회 |
| 8 | GET | `/api/stocks` | **REST** (JSON) | 종목 자동완성 |
| 9 | GET/POST/PUT/DELETE | `/api/shortcuts*` | **REST** (JSON) | 단축키 프리셋 CRUD |

> 왜 채팅만 SSE인가: 질문 하나가 카드(계산 완료 즉시) → 해설(LLM 재호출 후)처럼 **시간차 나는 조각들**을 만들어, 준비되는 대로 밀어내기 위해서. 나머지 엔드포인트는 요청-응답 한 번으로 끝나므로 REST.

---

## 1. 채팅 — SSE (핵심 API)

### Description
사용자의 자연어 질문을 받아 **지표 답변 카드 / 종목 되묻기 / 가이드 카드**를 SSE로 스트리밍하는 핵심 대화 API.
LLM은 도구 선택·해설 텍스트만 담당하고, **모든 수치 계산은 백엔드**가 수행한다.

### Base URL
`http://localhost:8080`

### Endpoint
`POST /api/chat/{sessionId}`

### Request
| **Parameter** | **Type** | **Description** |
| --- | --- | --- |
| sessionId | string (path) | 클라이언트가 생성한 세션 UUID |
| message | string (body) | 사용자의 자연어 질문 |

### Status Code
- `200 OK` SSE 스트림 시작 (`text/event-stream`)
- `400 Bad Request` message 누락/빈 값 등 요청 오류
- `500 Internal Server Error` 서버 내부 오류
> 스트림 시작 후 개별 실패는 HTTP 상태가 아니라 SSE `error` 이벤트로 전달

### ⭐ Response (SSE 이벤트) ⭐
- `Content-Type: text/event-stream`
- 이벤트 순서: `(card | clarify | guide)*` → `text` → `done` / 실패 시 `error` 후 종료
- 각 이벤트는 `event:` 줄과 `data:`(JSON) 줄로 구성되고, **빈 줄(`\n\n`)이 이벤트 구분자**다.
- ⚠️ 프론트 소비 주의: `EventSource`는 GET 전용이라 사용 불가(이 API는 POST). `fetch` 스트림으로 받고, 이벤트가 네트워크 청크 경계에서 잘릴 수 있으니 버퍼로 이어붙여 `\n\n` 단위로 파싱한다.

| **event** | **발생 시점** | **data 요약** |
| --- | --- | --- |
| `card` | 지표 계산 성공 | 지표 답변 카드 전문 (MetricResult) |
| `clarify` | 종목명 다건 매칭 | 후보 종목 목록 (되묻기 칩) |
| `guide` | 카탈로그 밖 질문 | 필요 CHECK API 레시피 |
| `text` | 응답 마지막 직전 | LLM 해설 텍스트 1건 |
| `done` | 정상 종료 | 스트림 종료 신호 (본문 없음) |
| `error` | 처리 실패 | 실패 사유 |

#### SSE 이벤트 1. event: `card` (MetricResult) = 카드 화면
프론트가 이 JSON을 그대로 렌더 = 카드 화면. 모든 수치는 **백엔드 계산값**(LLM 아님).

| **필드** | **타입** | **의미** |
| --- | --- | --- |
| cardId | string | 카드 UUID (xlsx 다운로드 `/api/cards/{cardId}/xlsx` 키) |
| metric | string | 지표 종류 코드 (예: `RETURN_GAP`) |
| title | string | 카드 제목 |
| from / to | string(date) | 조회 시작일 / 종료일 (`YYYY-MM-DD`) |
| targets[] | object[] | 분석 대상 `{ code, name }` |
| headline[] | object[] | 핵심 수치 `{ label, value, unit }` — 카드 상단 큰 숫자 |
| chart.chartType | string | `line`(추이) / `bar`(비교) |
| chart.series[] | object[] | 계열 `{ name, points[] }` |
| chart.series[].points[] | object[] | 점 `{ label(X축), value(Y축) }` |
| evidence.apiCalls[] | object[] | 근거① 호출 API `{ apiId, api, request, specUrl }`. `apiId`는 명세 인덱스 식별자(예: `stock-daily`)로 화면에는 안 보이지만, 카드의 "구현 방법 자세히"가 이 지표가 실제로 호출한 API를 정확히 지목하는 데 쓴다 |
| evidence.rawData[] | object[] | 근거② 원본 수치 `{ name, rows[{date,value}] }` |
| evidence.formula | string | 근거③ 적용 공식 |
| evidence.steps[] | object[] | 근거④ 중간 계산 `{ label, detail }` |

#### SSE 이벤트 2. event: `clarify` = 종목 되묻기
| **필드** | **타입** | **의미** |
| --- | --- | --- |
| query | string | 입력한 모호한 종목명 |
| candidates[] | object[] | 후보 종목(칩 버튼) `{ code, name, market }` — market: `KOSPI`/`KOSDAQ`/`INDEX` |

#### SSE 이벤트 3. event: `guide` = 가이드(레시피) 카드
| **필드** | **타입** | **의미** |
| --- | --- | --- |
| topic | string | 사용자가 물은 주제 |
| status | string | `ok` \| `no_match`. `no_match`는 `matched`·`catalog`가 모두 빈 경우 — CHECK API 명세에서 근거를 찾지 못했다는 뜻이고, LLM은 레시피를 지어내는 대신 안내할 수 없다고 답한다. `explain_metric_recipe`에는 실리지 않는다 |
| knownMetric | boolean | `true`면 **카탈로그에 있는 지표의 구현 방법**을 물은 경우(`explain_metric_recipe`). 카드가 "카탈로그에 없는 지표입니다"라고 말하지 않고 "카탈로그 추가 요청" 버튼도 감춘다. 생략되면 `false` |
| matched[] | object[] | 필요 CHECK API 상세 `{ apiId, name, path, summary, params[], docUrl, fields[] }`. `knownMetric`이면 키워드 검색 결과가 아니라 **그 지표가 실제로 호출한 API**다 |
| matched[].params[] | object[] | 파라미터 `{ name, required }` |
| matched[].fields[] | object[] | 응답 필드 `{ code, label }` |
| catalog[] | object[] | 참고 후보 API `{ apiId, name, summary }`. `knownMetric`이면 빈 배열 |

#### SSE 이벤트 4. event: `text` / `done` / `error`
| **event** | **data** | **의미** |
| --- | --- | --- |
| `text` | `{ "text": string }` | LLM 해설 문구 1건 |
| `done` | `{}` | 스트림 정상 종료 신호 |
| `error` | `{ "message": string }` | 처리 실패 사유 |

### Example

**Request**
```json
POST /api/chat/11111111-1111-4111-8111-111111111111
Content-Type: application/json

{ "message": "삼성전자랑 코스피 최근 한 달 수익률 갭 알려줘" }
```

**Response (정상 — 지표 카드)** — 원본 스트림 형태(wire format):
```text
event: card
data: {"cardId":"2f1c9a80-...","metric":"RETURN_GAP", ... }

event: text
data: {"text":"최근 한 달 삼성전자가 코스피 대비 3.0%p 높았습니다."}

event: done
data: {}
```

`card` 이벤트의 data (전체, 유효 JSON):
```json
{
  "cardId": "2f1c9a80-1b2c-4d3e-8a9f-0123456789ab",
  "metric": "RETURN_GAP",
  "title": "삼성전자 vs 코스피 수익률 갭 (최근 1개월)",
  "from": "2026-06-19",
  "to": "2026-07-19",
  "targets": [
    { "code": "005930", "name": "삼성전자" },
    { "code": "KOSPI", "name": "코스피" }
  ],
  "headline": [
    { "label": "수익률 갭", "value": 3.0, "unit": "%p" }
  ],
  "chart": {
    "chartType": "line",
    "series": [
      {
        "name": "삼성전자",
        "points": [
          { "label": "2026-06-19", "value": 0.0 },
          { "label": "2026-07-19", "value": 8.2 }
        ]
      },
      {
        "name": "코스피",
        "points": [
          { "label": "2026-06-19", "value": 0.0 },
          { "label": "2026-07-19", "value": 5.2 }
        ]
      }
    ]
  },
  "evidence": {
    "apiCalls": [
      {
        "apiId": "stock-daily",
        "api": "주식 일별 시세",
        "request": "005930 / 2026-06-19~2026-07-19",
        "specUrl": "https://checkapi.koscom.co.kr/spec/stock-daily"
      }
    ],
    "rawData": [
      {
        "name": "삼성전자",
        "rows": [
          { "date": "2026-06-19", "value": 81500.0 },
          { "date": "2026-07-19", "value": 88200.0 }
        ]
      }
    ],
    "formula": "(마지막 종가 / 첫 종가 − 1) × 100",
    "steps": [
      { "label": "삼성전자 수익률", "detail": "88200 / 81500 − 1 = 8.2%" },
      { "label": "코스피 수익률", "detail": "5.2%" },
      { "label": "수익률 갭", "detail": "8.2% − 5.2% = 3.0%p" }
    ]
  }
}
```

**Response (되묻기 — 모호한 종목명)** — `clarify` 이벤트의 data:
```json
{
  "query": "삼성",
  "candidates": [
    { "code": "005930", "name": "삼성전자", "market": "KOSPI" },
    { "code": "009150", "name": "삼성전기", "market": "KOSPI" },
    { "code": "018260", "name": "삼성SDS", "market": "KOSPI" }
  ]
}
```

**Response (가이드 — 카탈로그 밖 질문)** — `guide` 이벤트의 data:
```json
{
  "topic": "외국인 순매수 동향",
  "status": "ok",
  "matched": [
    {
      "apiId": "stock-investor",
      "name": "투자자별 매매동향",
      "path": "/stock/m001/invest_trend",
      "summary": "종목별 기관·외국인·개인 순매수 추이",
      "params": [
        { "name": "code", "required": true },
        { "name": "fromDate", "required": true },
        { "name": "toDate", "required": true }
      ],
      "docUrl": "https://checkapi.koscom.co.kr/spec/stock-investor",
      "fields": [
        { "code": "F0001", "label": "외국인 순매수" },
        { "code": "F0002", "label": "기관 순매수" }
      ]
    }
  ],
  "catalog": [
    { "apiId": "stock-investor", "name": "투자자별 매매동향", "summary": "종목별 순매수 추이" }
  ]
}
```

**Response (실패)**
```text
event: error
data: {"message":"일시적인 오류가 발생했습니다. 다시 시도해 주세요."}
```

---

## 2. 새 대화 — 세션 컨텍스트 폐기

### Description
`DELETE /api/chat/{sessionId}`

사용자가 "새 대화"를 누르면 서버가 들고 있는 **살아 있는 대화 컨텍스트**(Redis)를 즉시 버린다.
프론트가 localStorage의 세션 UUID만 새로 만들면 이전 컨텍스트는 TTL(`kopilot.session-ttl`, 기본 2시간) 동안 남는다.

영속 이력(`chat_log`)과 저장된 카드(`card`)는 지우지 않는다 — 지우는 것은 LLM에 재전송되는 맥락뿐이다.

### Request
| 이름 | 타입 | 설명 |
| --- | --- | --- |
| sessionId | string (path) | 클라이언트가 생성한 세션 UUID |

본문 없음.

### Response
- `204 No Content` — 폐기 완료. 존재하지 않는 세션에도 204(멱등)
- `400 Bad Request` — sessionId 형식 위반 (`^[A-Za-z0-9_-]{8,64}$`)

### Example
```
DELETE /api/chat/11111111-1111-4111-8111-111111111111
→ 204 No Content
```

---

## 3. 카드 엑셀(xlsx) 다운로드 — REST

### Description
답변 카드의 계산 결과를 Excel 파일로 다운로드 (결과요약 / 원본데이터 / 계산과정 3시트).
### Endpoint
`GET /api/cards/{cardId}/xlsx`
### Request
| Parameter | Type | Description |
| --- | --- | --- |
| cardId | string (path) | 카드 UUID (SSE `card`의 cardId) |
### Status Code
- `200 OK` xlsx 파일 (`application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`)
- `404 Not Found` 카드 없음
- `500 Internal Server Error` 파일 생성 오류
### Response
JSON 아님 — **엑셀 바이너리 파일** (필드 없음). 파일명 `kopilot-{cardId앞8자}.xlsx`.
### Example
```text
GET /api/cards/2f1c9a80-1b2c-4d3e-8a9f-0123456789ab/xlsx

200 OK
Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet
Content-Disposition: attachment; filename=kopilot-2f1c9a80.xlsx
(binary)
```

---

## 4. 카탈로그 추가 요청 (수요조사) — REST

### Description
카탈로그에 없는 지표에 대한 수요를 명시적으로 기록 (가이드 카드의 "카탈로그 추가 요청" 버튼).
### Endpoint
`POST /api/catalog-requests`
### Request
| Parameter | Type | Description |
| --- | --- | --- |
| sessionId | string (body, nullable) | 세션 UUID |
| topic | string (body) | 요청 지표 주제(집계 키) |
| matchedApiIds | string (body, nullable) | 가이드가 제시한 CHECK API id (콤마 구분) |
### Status Code
- `201 Created` 기록 성공 (source=EXPLICIT)
- `400 Bad Request` topic 누락
- `500 Internal Server Error` 서버 오류
### Response
본문 없음 (`201 Created`).
### Example
```json
POST /api/catalog-requests
Content-Type: application/json

{
  "sessionId": "11111111-1111-4111-8111-111111111111",
  "topic": "외국인 순매수 동향",
  "matchedApiIds": "stock-investor,stock-foreign"
}
```

---

## 5. 관리자 — 수요 요약 (REST)

### Description
수요조사 집계를 요청수 내림차순으로 조회.
### Endpoint
`GET /api/admin/demand/summary`
### Request
| Parameter | Type | Description |
| --- | --- | --- |
| limit | number (query, default 50) | 반환할 상위 항목 수 |
| X-Admin-Token | string (header) | 관리자 토큰 (또는 `?token=`) |
### Status Code
- `200 OK` DemandSummary 목록
- `404 Not Found` 토큰 불일치/누락 (401 대신 404로 존재 은닉)
### Response — `DemandSummary[]`
| Field | Type | Description |
| --- | --- | --- |
| topic | string | 수요 주제(집계 키) |
| requestCount | number | 총 요청 수 |
| explicitCount | number | "추가 요청" 버튼(EXPLICIT) 수 |
| sessionCount | number | 요청한 서로 다른 세션 수 |
| matchedApiIds | string | 관련 CHECK API id (콤마 구분) |
| lastAt | string | 마지막 요청 시각 |
### Example
```json
GET /api/admin/demand/summary?limit=50
header X-Admin-Token: kopilot-demo

200 OK
[
  {
    "topic": "외국인 순매수 동향",
    "requestCount": 12,
    "explicitCount": 3,
    "sessionCount": 8,
    "matchedApiIds": "stock-investor,stock-foreign",
    "lastAt": "2026-07-25T09:41:00Z"
  }
]
```

---

## 6. 관리자 — 통계 (REST)

### Description
발표용 집계 통계(질문·카드·가이드 수, 카탈로그 응답 비율) 조회.
### Endpoint
`GET /api/admin/stats`
### Request
| Parameter | Type | Description |
| --- | --- | --- |
| X-Admin-Token | string (header) | 관리자 토큰 (또는 `?token=`) |
### Status Code
- `200 OK` 통계 반환
- `404 Not Found` 토큰 불일치/누락
### Response
| Field | Type | Description |
| --- | --- | --- |
| questionCount | number | 누적 질문 수 |
| cardCount | number | 생성된 답변 카드 수 |
| guideCount | number | 가이드(카탈로그 밖) 발생 수 |
| catalogCoverageRate | number | 카탈로그가 답한 비율(%) = `1 − guideCount/questionCount` |
### Example
```json
GET /api/admin/stats
header X-Admin-Token: kopilot-demo

200 OK
{ "questionCount": 20, "cardCount": 14, "guideCount": 6, "catalogCoverageRate": 70 }
```

---

## 7. 지표 카탈로그 조회 — REST

### Description
단축키 프리셋 폼이 지표 목록과 프롬프트 템플릿을 받아 간다. 카탈로그의 단일 출처는 백엔드 실행기다.

### Endpoint
`GET /api/catalog`

### Status Code
- `200 OK` CatalogItem 배열

### Response — `CatalogItem[]`
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

| **필드** | **타입** | **의미** |
| --- | --- | --- |
| toolName | string | 지표 코드 (예: `return_gap`, `price_gap` 등) |
| label | string | 지표 한글명 |
| description | string | 지표 설명 |
| promptTemplate | string | 프롬프트 템플릿 (치환 토큰: `{targets}`·`{period}`) |
| minTargets | number | 최소 종목 개수 |
| maxTargets | number | 최대 종목 개수 |

**참고**: `promptTemplate`의 치환 토큰은 `{targets}`·`{period}` 둘뿐이다. `{period}`가 없으면 그 지표는 기간을 받지 않는다.

### Example
```json
GET /api/catalog

200 OK
[
  {
    "toolName": "return_gap",
    "label": "수익률 갭 비교",
    "description": "두 대상의 기간수익률 차이를 계산한다.",
    "promptTemplate": "{targets}의 {period} 수익률 갭을 비교해줘",
    "minTargets": 2,
    "maxTargets": 2
  }
]
```

---

## 8. 종목 자동완성 — REST

### Description
단축키 폼의 종목 검색. 되묻기와 같은 검색기(`StockResolver`)를 쓴다.

### Endpoint
`GET /api/stocks?q=<query>&limit=<limit>`

### Request
| Parameter | Type | Description |
| --- | --- | --- |
| q | string (query) | 검색어 (2자 이상, 이하면 빈 배열) |
| limit | number (query, default 8) | 반환할 항목 수 (기본값 8, 최대 20) |

### Status Code
- `200 OK` StockInfo 배열

### Response — `StockInfo[]`
```json
[
  {
    "code": "005930",
    "name": "삼성전자",
    "market": "KOSPI",
    "type": "STOCK"
  }
]
```

| **필드** | **타입** | **의미** |
| --- | --- | --- |
| code | string | 종목 코드 (6자리) 또는 지수 식별자(`KOSPI`, `KOSDAQ`) |
| name | string | 종목명 또는 지수명 |
| market | string | 소속 시장: `KOSPI` / `KOSDAQ` / `INDEX` |
| type | string | 종류: `STOCK` / `ETF` / `INDEX` |

### Example
```json
GET /api/stocks?q=삼성&limit=8

200 OK
[
  { "code": "005930", "name": "삼성전자", "market": "KOSPI", "type": "STOCK" },
  { "code": "009150", "name": "삼성전기", "market": "KOSPI", "type": "STOCK" }
]
```

---

## 9. 단축키 프리셋 — REST

### Description
종목·지표·기간을 묶어 키 조합에 걸어 두는 프리셋. 로그인이 없으므로 소유자는 브라우저가 발급한 `X-Device-Id`다.

### Endpoint
| 메서드 | 경로 | 응답 코드 |
|---|---|---|
| GET | `/api/shortcuts` | 200 |
| POST | `/api/shortcuts` | 201 |
| PUT | `/api/shortcuts/{id}` | 200 |
| DELETE | `/api/shortcuts/{id}` | 204 |

### Common Request
헤더 `X-Device-Id: <UUID>` 필수 (최대 64자).

### Request Body (POST / PUT)
```json
{
  "keyCombo": "ctrl+shift+1",
  "toolName": "return_gap",
  "targets": ["삼성전자(005930)", "SK하이닉스(000660)"],
  "period": "3M",
  "prompt": "삼성전자와 SK하이닉스의 최근 3개월 수익률 갭을 비교해줘"
}
```

| **필드** | **타입** | **검증 규칙** |
| --- | --- | --- |
| keyCombo | string | `ctrl+shift+<숫자\|영문>` 형식 (소문자 a-z·0-9 만) |
| toolName | string | 카탈로그에 있는 지표명 |
| targets | string[] | 개수는 해당 지표의 `minTargets`~`maxTargets` 안 |
| period | string \| null | `1M` \| `3M` \| `6M` \| `1Y` \| null |
| prompt | string | 1~300자 (실제로 전송될 문구) |

### Response — `ShortcutView`
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "keyCombo": "ctrl+shift+1",
  "toolName": "return_gap",
  "targets": ["삼성전자(005930)", "SK하이닉스(000660)"],
  "period": "3M",
  "prompt": "삼성전자와 SK하이닉스의 최근 3개월 수익률 갭을 비교해줘"
}
```

| **필드** | **타입** |
| --- | --- |
| id | string (UUID) |
| keyCombo | string |
| toolName | string |
| targets | string[] |
| period | string \| null |
| prompt | string |

### Status Code & Errors

| 코드 | 상황 | 바디 `code` | 바디 `message` |
|---|---|---|---|
| 200 | GET/PUT 성공 | — | — |
| 201 | POST 성공 | — | — |
| 204 | DELETE 성공 | — | — |
| 400 | 검증 실패 | 아래 참고 | 상세 메시지 |
| 404 | 프리셋 없음 또는 다른 기기 | `NOT_FOUND` | 단축키를 찾을 수 없습니다 |
| 409 | 같은 기기에서 이미 쓰는 키 | `KEY_TAKEN` | 이미 사용 중인 키 조합입니다: ... |

**400 Bad Request 에러 코드**:
| `code` | 의미 |
|---|---|
| `KEY_COMBO_INVALID` | 키 조합 형식 위반 |
| `TOOL_UNKNOWN` | 지표가 없거나 단축키 대상이 아님 |
| `TARGET_COUNT_INVALID` | 종목 개수가 해당 지표 범위 밖 |
| `PROMPT_INVALID` | 프롬프트 길이 위반 (1~300자 아님) |
| `DEVICE_ID_INVALID` | X-Device-Id 헤더 누락 또는 형식 위반 |

에러 응답 형식:
```json
{
  "code": "KEY_COMBO_INVALID",
  "message": "키 조합은 ctrl+shift+<숫자·영문> 형식이어야 합니다"
}
```

### Example

**GET — 프리셋 목록 조회**
```
GET /api/shortcuts
header X-Device-Id: 11111111-1111-4111-8111-111111111111

200 OK
[
  {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "keyCombo": "ctrl+shift+1",
    "toolName": "return_gap",
    "targets": ["삼성전자(005930)", "SK하이닉스(000660)"],
    "period": "3M",
    "prompt": "삼성전자와 SK하이닉스의 최근 3개월 수익률 갭을 비교해줘"
  }
]
```

**POST — 프리셋 생성**
```json
POST /api/shortcuts
header X-Device-Id: 11111111-1111-4111-8111-111111111111
Content-Type: application/json

{
  "keyCombo": "ctrl+shift+1",
  "toolName": "return_gap",
  "targets": ["삼성전자(005930)", "SK하이닉스(000660)"],
  "period": "3M",
  "prompt": "삼성전자와 SK하이닉스의 최근 3개월 수익률 갭을 비교해줘"
}

201 Created
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "keyCombo": "ctrl+shift+1",
  "toolName": "return_gap",
  "targets": ["삼성전자(005930)", "SK하이닉스(000660)"],
  "period": "3M",
  "prompt": "삼성전자와 SK하이닉스의 최근 3개월 수익률 갭을 비교해줘"
}
```

**PUT — 프리셋 수정**
```json
PUT /api/shortcuts/550e8400-e29b-41d4-a716-446655440000
header X-Device-Id: 11111111-1111-4111-8111-111111111111
Content-Type: application/json

{
  "keyCombo": "ctrl+shift+2",
  "toolName": "price_gap",
  "targets": ["삼성전자(005930)"],
  "period": "1M",
  "prompt": "삼성전자 1개월 주가 변동"
}

200 OK
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "keyCombo": "ctrl+shift+2",
  "toolName": "price_gap",
  "targets": ["삼성전자(005930)"],
  "period": "1M",
  "prompt": "삼성전자 1개월 주가 변동"
}
```

**DELETE — 프리셋 삭제**
```
DELETE /api/shortcuts/550e8400-e29b-41d4-a716-446655440000
header X-Device-Id: 11111111-1111-4111-8111-111111111111

204 No Content
```

**Error Response Example — 키 조합 형식 위반**
```json
400 Bad Request
{
  "code": "KEY_COMBO_INVALID",
  "message": "키 조합은 ctrl+shift+<숫자·영문> 형식이어야 합니다"
}
```

**Error Response Example — 같은 키 이미 사용 중**
```json
409 Conflict
{
  "code": "KEY_TAKEN",
  "message": "이미 사용 중인 키 조합입니다: ctrl+shift+1"
}
```
