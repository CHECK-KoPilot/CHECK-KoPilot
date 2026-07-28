# 단축키 프리셋 — 설계

작성일 2026-07-28

## 문제

자주 던지는 질문일수록 매번 같은 문장을 다시 친다. "삼성전자와 SK하이닉스의 최근 3개월 수익률 갭"을 하루에도 몇 번 묻는 트레이더에게 채팅 입력은 반복 노동이다.

## 해결

종목·지표·기간을 미리 묶어 키 조합에 걸어 두고, 키를 누르면 그 질문이 **즉시 전송**된다. 화면 상단의 "단축키" 버튼에서 만들고 관리한다.

단축키는 새로운 실행 경로가 아니다. 저장해 둔 문구를 기존 채팅 전송 함수에 그대로 넣을 뿐이며, 계산·근거 패널·고지 문구는 현재 흐름과 완전히 같다.

## 범위

**한다**: 프리셋 CRUD, 자동완성 종목 선택, 카탈로그 선택, 기간 선택, 프롬프트 자동 생성 및 편집, 키 조합 캡처, 전역 키 트리거.

**하지 않는다**: 프리셋 폴더·정렬, 팀 공유, 임포트/익스포트, 실행 이력, 로그인 연동.

## 결정 사항

| 항목 | 결정 | 이유 |
|---|---|---|
| 키를 누르면 | 즉시 전송 | "누르면 바로 나온다"가 이 기능의 요구다. 한 번 더 확인받으면 절약한 손이 되돌아온다 |
| 저장 위치 | MySQL + REST | 브라우저 저장소를 비워도 프리셋이 남는다 |
| 소유자 키 | `deviceId` (신규) | `sessionId`는 "새 대화"마다 바뀐다(`session.js:37`) — 프리셋을 묶을 키가 못 된다 |
| 종목 선택 | 자동완성 검색 | 마스터가 4,392행이라 목록으로 못 세운다. `StockResolver.search()`가 이미 있다 |
| 프롬프트 | 자동 생성 + 편집 가능 | 지표별 특수 파라미터(이동평균 일수 등)를 폼 필드로 늘리지 않고 흡수한다 |
| 카탈로그 메타 | 백엔드 `GET /api/catalog` | 카탈로그의 단일 출처는 executor다. 지표를 추가해도 프론트를 고칠 일이 없다 |
| 키 범위 | `Ctrl(⌘)+Shift+<숫자·영문>` | 브라우저 예약 조합(Ctrl+T/W/N, Alt+숫자 탭 전환)을 피한다 |

## 백엔드

### 인터페이스 확장

`MetricExecutor`에 default 메서드 하나만 추가한다.

```java
default PresetSpec presetSpec() { return null; }   // null = 단축키 폼에 노출하지 않음

record PresetSpec(String label, String promptTemplate, int minTargets, int maxTargets) {}
```

- `label` — 드롭다운에 쓰는 짧은 한글 이름("수익률 갭 비교"). `description()`은 LLM tool 선택용이라 사람이 고를 목록에는 길다.
- `promptTemplate` — `"{targets}의 {period} 수익률 갭을 비교해줘"`. 치환 토큰은 `{targets}`, `{period}` 둘뿐이다. `{targets}`에는 칩의 **종목명만** "와"/", "로 이어 넣는다 — 코드까지 넣으면 문장이 읽기 나빠진다. 동명 종목이 걱정되면 사용자가 프롬프트를 직접 고쳐 코드를 적을 수 있다.
- `{period}` 유무가 폼의 기간 셀렉트 노출 여부를 결정한다. 별도 플래그를 두지 않는다.
- `minTargets`/`maxTargets` — 종목 칩 개수 검증에 쓴다.

지표 7종이 각각 한 줄로 구현한다. default 구현은 새 실행기가 이 메서드를 잊어도 컴파일이 깨지지 않게 하는 안전판이며, `presetSpec()`이 null인 지표는 단축키 폼에 나오지 않는다.

### 엔드포인트

세 개 모두 `docs/api.md`에 **같은 PR로** 추가한다.

**GET `/api/catalog`**

```json
[
  {
    "toolName": "return_gap",
    "label": "수익률 갭 비교",
    "description": "두 대상(종목/지수/ETF)의 기간수익률 차이를 계산한다. …",
    "promptTemplate": "{targets}의 {period} 수익률 갭을 비교해줘",
    "minTargets": 2,
    "maxTargets": 2
  }
]
```

`CatalogService`가 들고 있는 `List<MetricExecutor>`를 그대로 투영한다. `presetSpec()`이 null인 항목은 제외한다.

**GET `/api/stocks?q=삼성&limit=8`**

```json
[{ "code": "005930", "name": "삼성전자", "market": "KOSPI", "type": "STOCK" }]
```

`StockResolver.search()`에 위임한다. `limit`은 기본 8, 최대 20. `q`가 2자 미만이면 빈 배열(무의미한 전체 스캔 차단).

**`/api/shortcuts`** — 소유자는 `X-Device-Id` 헤더로 넘긴다.

| 메서드 | 경로 | 동작 |
|---|---|---|
| GET | `/api/shortcuts` | 그 기기의 프리셋 목록 (created_at 오름차순) |
| POST | `/api/shortcuts` | 생성. 응답 201 + 저장된 프리셋 |
| PUT | `/api/shortcuts/{id}` | 수정 |
| DELETE | `/api/shortcuts/{id}` | 삭제. 응답 204 |

상태 코드: 키 조합 중복 **409**, 검증 실패 **400**, 남의 `deviceId` 프리셋 접근 **404**(존재 여부를 알려 주지 않는다).

### 테이블

`schema.sql`에 추가한다. 기존 관례대로 `IF NOT EXISTS`.

```sql
CREATE TABLE IF NOT EXISTS shortcut (
    id         CHAR(36)     NOT NULL,
    device_id  VARCHAR(64)  NOT NULL,
    key_combo  VARCHAR(40)  NOT NULL,   -- 정규화 문자열 "ctrl+shift+1"
    tool_name  VARCHAR(60)  NOT NULL,   -- 폼 재편집용 메타
    targets    VARCHAR(255) NOT NULL,   -- 콤마 구분 종목명
    period     VARCHAR(20)  NULL,       -- 1M | 3M | 6M | 1Y (없는 지표는 NULL)
    prompt     VARCHAR(300) NOT NULL,   -- 실제로 전송되는 문구
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_shortcut_device_key (device_id, key_combo),
    KEY idx_shortcut_device (device_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

`targets`는 재편집 시 칩을 정확히 되살리기 위해 `삼성전자(005930)` 형식으로 저장한다(되묻기 카드가 쓰는 표기와 같다). `period`는 코드(`3M`)로 저장하고, 표시 문구("최근 3개월")와 프롬프트에 들어갈 한글 표현의 매핑은 프론트 `promptTemplate.js`가 갖는다 — 지표 계산은 이 값을 보지 않으므로 백엔드에 둘 이유가 없다.

`prompt`가 전송의 단일 진실이다. 사용자가 폼에서 고친 문구가 그대로 저장되므로 실행 시점에 문구를 다시 만드는 로직이 끼어들 여지가 없다. `tool_name`·`targets`·`period`는 프리셋을 다시 열어 편집할 때만 쓰는 메타데이터다.

키 중복은 유니크 제약이 막는다. 프론트가 먼저 경고하더라도 최종 판정은 DB가 한다.

### 모듈

새 패키지 `com.koscom.kopilot.shortcut` 하나로 끝난다.

- `ShortcutController` — REST 4개
- `JdbcShortcutStore` — CRUD
- `Shortcut` — 레코드
- 검증: 키 패턴 `^ctrl\+shift\+[a-z0-9]$`, `prompt` 1~300자, `toolName`이 카탈로그에 있는지, `targets` 개수가 **그 지표의 `minTargets`~`maxTargets`** 안인지(고정 상한을 두면 `return_ranking`의 2~10개와 어긋난다)

계산 로직이 없으므로 `catalog`·`chat`·`checkapi`의 기존 흐름은 건드리지 않는다. `MetricExecutor`에 default 메서드가 하나 붙는 것이 유일한 기존 코드 변경이다.

## 프론트엔드

### 파일

```
lib/deviceId.js          kopilot.deviceId UUID 발급·보관 (session.js의 randomUUID 재사용)
lib/keyCombo.js          keydown → "ctrl+shift+1" 정규화 / 표시 포맷 / 허용 여부 판정
lib/promptTemplate.js    {targets} {period} 치환 — 순수함수
lib/shortcutsApi.js      shortcuts CRUD + catalog + stocks fetch
hooks/useShortcuts.js    목록 상태 + CRUD + window keydown 바인딩
components/shortcuts/
  ShortcutMenu.jsx       헤더 버튼 + 드롭다운(목록·추가·수정·삭제)
  ShortcutFormModal.jsx  저장 폼
  StockPicker.jsx        자동완성 칩 입력
  KeyComboInput.jsx      키 캡처 인풋
```

`GlobalHeader`는 `actions` prop을 받아 프로필 왼쪽에 슬롯만 연다. `AppLayout`이 `ChatPage`에서 받아 통과시킨다. 헤더는 계속 표시만 담당한다.

### 데이터 흐름

```
ChatPage
  └ useShortcuts({ onTrigger: ask, enabled: !sending && !formOpen })
       ├ GET /api/shortcuts   (마운트 1회)
       └ window keydown → 매칭 → preventDefault → onTrigger(prompt) → 기존 SSE 경로
```

단축키는 `ask()`를 그대로 부른다. 전송 경로가 하나뿐이라 사용자가 직접 친 질문과 같은 흐름을 탄다.

### 저장 폼

```
종목    : [삼성전자 ×] [SK하이닉스 ×]  ← 입력하면 /api/stocks 자동완성
카탈로그 : [수익률 갭 비교      ▾]     ← /api/catalog
기간    : [최근 3개월          ▾]     ← 템플릿에 {period}가 있을 때만
키 조합  : [ Ctrl + Shift + 1 ]        ← 포커스 후 실제 키를 누르면 캡처
프롬프트 : ┌────────────────────────┐
          │ 삼성전자와 SK하이닉스의  │  ← 자동 생성, 그 자리에서 수정 가능
          │ 최근 3개월 수익률 갭을   │
          │ 비교해줘                │
          └────────────────────────┘  [다시 생성]
                                      [저장]
```

### 동작 규칙

- `Ctrl(mac은 ⌘)+Shift+<숫자·영문>`만 등록할 수 있다. 그 외 조합은 캡처 인풋이 거부하고 사유를 인라인으로 표시한다.
- IME 조합 중(`e.isComposing`)에는 트리거하지 않는다 — 한글 입력 중 오발사 방지.
- 답변 스트리밍 중(`sending`)이거나 폼이 열려 있으면 트리거를 끈다. 폼이 열려 있을 때 끄지 않으면 키를 캡처하는 순간 그 단축키가 발사된다.
- 매칭되면 `preventDefault()`로 브라우저 기본 동작을 막는다.
- 프롬프트 미리보기는 종목·카탈로그·기간이 바뀔 때마다 재생성하되, **사용자가 한 번 손대면 그 뒤로는 덮어쓰지 않는다.** 편집한 문장이 조용히 사라지면 안 된다. "다시 생성" 버튼으로 되돌린다.

### 에러 처리

| 상황 | 처리 |
|---|---|
| 키 조합 중복 | 프론트가 목록 대조로 먼저 경고. 저장 시 서버 409면 충돌 상대 이름과 함께 폼 상단에 표시 |
| 목록 로드 실패 | 헤더 버튼은 남고 드롭다운에 "불러오지 못했습니다 · 다시 시도". 채팅은 영향 없음 |
| 종목 검색 실패 | 칩 입력이 자유 입력으로 흘러가고, 실행 시 기존 되묻기(clarify)가 받는다 |
| 저장 검증 실패(400) | 해당 필드 아래 인라인 메시지 |

## 테스트

**백엔드**

- `ShortcutControllerTest` — 같은 기기의 키 중복 409, 다른 `deviceId` 간 격리, 검증 실패 400, 남의 프리셋 삭제 404
- `CatalogControllerTest` — 지표 7종이 `presetSpec`을 모두 노출하고 응답에 라벨·템플릿이 실린다

**프론트엔드 (vitest)**

- `keyCombo` — 정규화, 허용되지 않는 조합 거부, mac/윈도우 표시 포맷
- `promptTemplate` — `{targets}` 다건 연결, `{period}` 치환, 토큰 없는 템플릿
- `useShortcuts` — 매칭 시 `onTrigger` 호출, IME 조합 중 무시, `enabled=false`일 때 무시
- `ShortcutFormModal` — 자동 생성 → 편집 → 저장, 409 응답 표시

## 문서

`docs/api.md`에 엔드포인트 3종을 같은 PR에서 추가한다. `CLAUDE.md`의 모듈 표에 `shortcut` 행을 넣는다.
