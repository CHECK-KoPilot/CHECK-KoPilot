# 설계: 모호한 종목명 되묻기 — LLM 경계 계약 + 후보 랭킹

- 작성일: 2026-07-28
- 상태: 사용자 승인
- 관련 스펙: [../../spec.md](../../spec.md) 5절 가드레일, [../../api.md](../../api.md) SSE `clarify` 이벤트

## 0. 구현 분할

근본 원인이 둘이고 서로 다른 계층에 있어 이슈를 나눠 진행한다.

| 이슈 | 범위 | 이 문서의 절 |
|---|---|---|
| #90 | LLM 경계 — tool 파라미터 계약, 시스템 프롬프트, eval 하네스 | §3-A, §3-B, §4 |
| #91 | 후보 랭킹 — 우선순위 CSV, ORDER BY, not_found 제안, 칩 왕복 | §3-C, §3-D, §3-E |

#90이 되묻기를 **뜨게** 하고, #91이 뜬 후보를 **쓸만하게** 한다. 둘 다 있어야
목표 동작(§2)이 성립하지만, #90 없이는 #91의 코드 경로에 도달하지 못하므로 #90이 먼저다.

## 1. 증상과 근본 원인

### 관측된 증상

"삼성 주가 요약해줘"가 되묻기 없이 **삼성전자** 카드로 답한다. `chat_log` id 279~280에 그 순간이 남아 있다.

```
user      : 삼성 최근 한 달 수익률 알려줘
tool_call : period_summary {"target":"삼성전자","from":"2026-06-27","to":"2026-07-27"}
```

**LLM이 "삼성"을 "삼성전자"로 치환해서 넘겼다.** 백엔드는 "삼성"을 본 적이 없으므로
`AmbiguousStockException`이 발생할 수가 없다. 되묻기 경로는 멀쩡한데 도달하지 못한 것이다.

같은 대화(id 265~266)에서 LLM은 종목 대신 **기간**을 되물었다. `SystemPrompt.java:33`이
"기간 때문에 되묻지 말 것"이라고 명시하는데도 그렇다. 한 턴에서 지시 두 개를 동시에 어겼다.

"하이닉스"로 물으면 되묻기가 뜨는 이유도 같은 원리다 — 공식명이 `SK하이닉스`라
LLM이 완성할 여지가 적어 원문이 그대로 넘어가고, 그제서야 백엔드 판정이 작동한다.

### 근본 원인 ①: tool 파라미터 계약이 종목명 완성을 요구한다

실행기 7종 전부 `target` 설명이 `"대상의 한글 종목명/지수명"`이다
(`PeriodSummaryExecutor.java:31`, `CumulativeReturnExecutor.java:39`, `ReturnGapExecutor.java:34-35`,
`MaDisparityExecutor.java:29`, `NavDisparityExecutor.java:27`, `VolatilityExecutor.java:29`,
`ReturnRankingExecutor.java:31`).

"삼성"은 종목명이 아니다. 스키마가 **유효한 종목명을 달라고 요구하므로**, 지시를 잘 따르는
모델은 이를 유효한 값으로 완성해서 채운다. 모델이 규칙을 어긴 게 아니라 규칙대로 한 것이다.

`SystemPrompt.java:17`의 "종목은 한글 이름 그대로 tool에 전달한다(코드 변환은 시스템이 수행)"는
괄호 때문에 *'코드로 바꾸지 말라'*로만 읽히고, *'이름을 완성하지 말라'*는 금지로는 읽히지 않는다.

### 근본 원인 ②: 되묻기 후보가 관련도로 정렬되지 않는다

`JdbcStockResolver.java:49`의 정렬이 `ORDER BY CHAR_LENGTH(name), name LIMIT 5` — 관련도가 아니라
**이름 길이 + 가나다순**이다. 실제 DB(4,392행)에서 재현한 결과:

| 입력 | 부분매칭 | 현재 후보 5개 |
|---|---|---|
| 삼성 | 97건 | 삼성공조, 삼성물산, 삼성생명, 삼성전기, 삼성전자 |
| 하이닉스 | 5건 | SK하이닉스, **레버리지 ETF 4종** |
| 현대 | 48건 | 현대차, HD현대, 현대건설, 현대공업, 현대로템 |

"삼성" 매칭 97건 중 STOCK은 26건뿐이고 ETN 52 + ETF 19건이다. 우선주 113개·스팩 73개도
후보 풀에 그대로 들어간다.

### 두 원인은 직렬로 걸려 있다

①만 고치면 clarify 카드는 뜨지만 칩이 엉망이고, ②만 고치면 애초에 뜨지 않는다.
목표 동작("삼성" → 되묻되 **삼성전자가 1번 칩**)은 둘 다 고쳐야 성립한다.

## 2. 목표 동작

```
사용자: 삼성 주가 요약해줘

AI: 어느 종목을 말씀하시는지 골라 주세요.
    [삼성전자] [삼성바이오로직스] [삼성SDI] [삼성물산] [삼성생명]
```

- 기간은 되묻지 않는다 — 없으면 최근 90일 기본값을 적용하고 카드에 적용 기간을 드러낸다.
- 공식 상장명 정확일치는 지금처럼 바로 해석한다(`한화`, `두산`, `LG`, `SK`는 실재하는 지주사 종목이다).

## 3. 설계

### A. tool 파라미터 계약 (근본 원인 ①의 1차 방어)

실행기 7종의 `target` / `targets` / `target_a` / `target_b` 설명을 **금지형 + 반례**로 교체한다.

```
"사용자가 말한 표현 그대로 넣는다. 회사명을 추측해 완성하지 말 것 —
 '삼성'을 '삼성전자'로, '하이닉스'를 'SK하이닉스'로 바꾸지 않는다.
 종목이 여러 개일 가능성은 백엔드가 판정해 되묻는다."
```

모델은 시스템 프롬프트보다 파라미터 스키마를 더 잘 따르므로 여기를 1차 방어로 둔다.
`NavDisparityExecutor`는 ETF 전용이라 기존 예시(`TIGER 미국S&P500`)를 유지하되 같은 금지 문구를 덧붙인다.

### B. SystemPrompt (2차 방어)

`[역할과 원칙]`의 종목 규칙을 아래로 대체한다. 괄호 안 부연이 규칙을 좁혀 읽히게 만들던 문제를 없앤다.

```
- 종목·지수는 사용자가 말한 표현을 **그대로** tool에 넘긴다. 종목코드로 바꾸지도,
  회사명을 추측해 완성하지도 않는다. "삼성"은 "삼성"으로 넘긴다 — "삼성전자"로 바꾸지 말 것.
  어느 종목인지 모호하면 그것을 판정하고 되묻게 하는 일은 백엔드 몫이다.
```

`[되묻기]` 절에는 `status=not_found` 규칙이 아예 없다. 아래를 추가한다.

```
- tool이 status=not_found를 반환하면 suggestions를 근거로 되묻는다. suggestions가 비어 있고
  입력이 종목명이 아니라 테마·업종어("이차전지", "반도체주")로 보이면 되묻지 말고 explain_recipe로 간다.
```

**기간 되묻기 위반**: `[대화 이어가기]`·`[되묻기]`에 흩어진 기간 규칙이 서로를 약화시킨다.
"기간 때문에 되묻지 말 것"을 `[역할과 원칙]` 상단으로 올려 단일 규칙으로 못 박고,
하위 절에서는 중복 서술하지 않는다.

#### 구현 중 추가로 드러난 것 — from/to 스키마가 계약과 달랐다

프롬프트만 고친 상태로 실앱을 8회 측정했더니 6회만 종목 되묻기였고 2회는 여전히 기간을 되물었다.
원인은 프롬프트가 아니라 여기서도 스키마였다.

기간을 받는 실행기 6종이 **전부** `parsePeriodOrRecent`(날짜 선택, 기본 90일)를 쓰는데,
스키마에 "생략 시" 를 적은 것은 `NavDisparity`·`CumulativeReturn` 2종뿐이었다. 나머지 4종
(`PeriodSummary`·`ReturnGap`·`Volatility`·`ReturnRanking`)은 `from`/`to`를 `"조회 시작일 YYYY-MM-DD"`로만
제시해 **필수처럼 보였다.** 시스템 프롬프트가 "기간은 되묻지 말라"고 못 박아도 스키마가 이기는 자리다.

target 버그와 같은 패턴이다 — 스키마가 실제 계약과 어긋나면 모델이 그 틈을 사용자에게 물어서 메운다.
문구가 또 어긋나지 않도록 `ExecutorSupport.FROM_DESC` / `TO_DESC` 상수로 묶어 6종이 공유한다.

### C. 후보 랭킹 (근본 원인 ②)

#### C-1. 우선순위 데이터

`stock_master`에 `priority INT NOT NULL DEFAULT 0` 컬럼을 추가한다(`schema.sql`, `IF NOT EXISTS` 관례상
`ALTER TABLE ... ADD COLUMN IF NOT EXISTS`). 값이 클수록 대표성이 높다.

원천은 `stock-aliases.csv` 옆의 신규 `stock-priority.csv`다. 형식은 `code,priority,종목명(주석용)`이고
3번째 필드는 사람이 읽기 위한 것으로 적재 시 무시한다. 초기 50~100종목.

```csv
# 대표종목 우선순위. 값이 클수록 되묻기 후보에서 앞에 온다.
# 3번째 필드는 주석 — 적재는 code,priority만 본다.
005930,100,삼성전자
000660,99,SK하이닉스
```

`StockMasterLoader`는 별칭과 **같은 패턴**으로 적재한다 — 한 트랜잭션 안에서
`UPDATE stock_master SET priority=0 WHERE priority<>0`으로 리셋한 뒤 재적재. csv가 원천이므로
csv에서 지운 종목의 우선순위가 DB에 남지 않는다. 마스터 UPSERT는 `name/market/type`만 갱신하므로
컬럼이 서로를 덮어쓰지 않는다. 마스터 → 별칭 → 우선순위 순으로 적재한다(우선순위는 마스터 행을 전제한다).

#### C-2. 정렬 규칙

`JdbcStockResolver.search()`의 `ORDER BY`를 아래로 교체한다. 실 DB에서 검증했다.

```sql
ORDER BY
  (c.name = ?) DESC,                                    -- 정확일치
  COALESCE(p.priority, 0) DESC,                         -- 대표종목
  (c.name LIKE ?) DESC,                                 -- 접두일치
  CASE c.type WHEN 'ETF' THEN 1 WHEN 'ETN' THEN 2 ELSE 0 END,
  (c.type = 'STOCK' AND RIGHT(c.code,1) <> '0') ASC,    -- 우선주 후순위
  (c.name LIKE '%스팩%') ASC,                            -- 스팩 후순위
  CHAR_LENGTH(c.name), c.name
LIMIT 5
```

MySQL 임시테이블 제약(같은 쿼리에서 두 번 참조 불가) 때문에 UNION 바깥에서 한 번만 조인한다.

**우선주 판정은 이름이 아니라 종목코드 끝자리로 한다.** 이름 정규식 `우B?$`는 `성우`(458650),
`에코글로우`, `이오플로우` 같은 보통주를 오탐한다. 코드 규칙(보통주 `0`, 우선주 `5/7/9/K/L`)은
실 DB 2,766 STOCK 전수에서 오탐 0건 · 미탐 0건으로 확인됐다.

유형·우선주·스팩은 **제외가 아니라 후순위**다 — "KODEX 200" 같은 정당한 ETF 질의를 막지 않기 위함이다.

검증 결과("삼성", 우선순위 30종목만 넣은 상태):

| | 1 | 2 | 3 | 4 | 5 |
|---|---|---|---|---|---|
| 현재 | 삼성공조 | 삼성물산 | 삼성생명 | 삼성전기 | 삼성전자 |
| 제안 | **삼성전자** | 삼성바이오로직스 | 삼성SDI | 삼성물산 | 삼성생명 |

### D. not_found 제안

`JdbcStockResolver.java:31`의 `q.substring(0, 2)` 재검색을 **연속 부분문자열 축약 탐색**으로 교체한다.
길이 2 이상인 모든 연속 부분문자열을 긴 것부터 시도해 첫 히트를 제안으로 쓴다.
"이차전지" → "이차전지"(0) → "이차전"(0) → "차전지"(23건) 히트.

### E. 칩 클릭 왕복

`ChatPage.jsx:160`이 `"삼성전자(005930) 기준으로 진행해줘"`를 보내면, LLM이 여기서 이름을
다시 뽑아내야 한다. 문자열 그대로 넘어오면 `LIKE '%삼성전자(005930)%'`로 0건 → not_found다.

`JdbcStockResolver.resolve()` 진입부에 `^(.+)\((\S+)\)$` 정규화를 넣어 괄호 안을 코드로 먼저 시도한다.
프론트 문구는 그대로 두고, LLM이 이름만·코드만·`이름(코드)` 중 무엇을 넘겨도 맞게 된다.

## 4. 테스트

### 결정적 테스트 — `JdbcStockResolverTest` (SpringBootTest, MySQL 필요)

| 케이스 | 기대 |
|---|---|
| `search("삼성")` 첫 후보 | `005930` |
| `search("하이닉스")` | ETF보다 `000660`이 앞 |
| 우선주·스팩 | 같은 접두 보통주보다 뒤 |
| `resolve("삼성전자(005930)")` | `005930` |
| `resolve("이차전지")` | `StockNotFoundException`, suggestions 비어 있지 않음 |
| 기존 8케이스 | 전부 유지(회귀) |

우선순위 CSV는 테스트가 의존하는 원천이므로, 위 케이스가 쓰는 종목은 csv에 반드시 넣는다.

### LLM 회귀 측정 — `EvalRunner` (`RUN_EVAL=true`, 실 OpenAI 호출)

#### 선결 조건: `params`로는 이 회귀를 잡을 수 없다

`EvalRunner.java:163`이 `got.contains(wanted)`로 비교한다. 따라서 `params: { target: "삼성" }`은
모델이 `"삼성전자"`를 보내도 **PASS한다** — `"삼성전자".contains("삼성")`이 참이기 때문이다.
지금 하네스로는 이 버그를 영영 측정할 수 없다.

`paramsExact` 키를 추가한다. `params`(부분 문자열)와 같은 자리에 쓰되 `.equals()`로 비교한다.
기존 `params` 의미는 그대로 두어 기존 20여 케이스가 회귀하지 않게 한다.

```java
for (Map.Entry<String, Object> e : exactParams.entrySet()) {
    String wanted = String.valueOf(e.getValue());
    String got = parsed.path(e.getKey()).asText("");
    if (!got.equals(wanted)) { /* FAIL */ }
}
```

배열 파라미터(`targets`)는 `path(key).asText("")`가 항상 `""`라 어느 쪽으로도 비교되지 않는다.
이번 케이스는 전부 문자열 파라미터(`target`, `target_a`)로 구성해 배열 지원은 넣지 않는다.

#### 추가 케이스

**지금 돌리면 FAIL하고 수정 후 PASS하는 것이 이 작업의 실패 테스트다.**

```yaml
  # ── 모호한 종목명은 보정하지 않고 그대로 넘긴다 (2026-07-28)
  - q: "삼성 주가 요약해줘"
    expect: period_summary
    paramsExact: { target: "삼성" }
  - q: "하이닉스 최근 한 달 시세 요약해줘"
    expect: period_summary
    paramsExact: { target: "하이닉스" }
  - q: "삼성이랑 코스피 최근 한 달 수익률 갭 알려줘"
    expect: return_gap
    paramsExact: { target_a: "삼성", target_b: "코스피" }
```

질문은 tool이 하나로 확정되는 표현만 쓴다("수익률"만 쓰면 `period_summary`와 `cumulative_return`
사이에서 흔들려 target 검증 이전에 tool 불일치로 떨어진다).

**기간 되묻기 회귀**는 `paramsExact`로 잡히지 않으므로 `expect`가 `no_tool`이 아님을 확인하는 것으로
갈음한다 — "삼성 주가 요약해줘"에 tool을 부르지 않고 기간을 되물으면 `actual=no_tool`로 FAIL한다.
위 3케이스가 이미 그 역할을 겸한다.

`MIN_ACCURACY = 90.0` 하한은 그대로 둔다.

## 5. 하지 않는 것

- **시가총액 기반 자동 랭킹.** `F15028`은 종목별 개별 조회뿐이라 2,766콜이 필요하고
  CHECK API에 시총 상위/전종목 랭킹 엔드포인트가 없다(`apis_full.json` 776건 확인, `순위정보` 계열은
  전부 투자자·공매도 수급 순위다). MVP에 과하다.
- **지주사 정확일치 변경.** `LG`·`SK`·`한화`·`두산`은 실재하는 상장 종목이므로 공식 상장명 우선 원칙을
  유지한다. "LG 주가"가 LG전자를 뜻하는 경우는 별도 이슈로 다룬다.
- **사용자 원문과 target 대조 검증.** 백엔드가 마지막 user 턴에 target이 부분문자열로 없으면
  거부하는 방어는 강제력이 있지만, 별칭·영문명·후속턴 이어받기에서 오탐이 크다. A·B로 부족하다고
  측정되면 그때 다시 검토한다.

## 6. 영향 범위

| 파일 | 변경 |
|---|---|
| `catalog/*Executor.java` (7) | `inputSchemaProperties()`의 target 설명 |
| `chat/SystemPrompt.java` | 종목 규칙·기간 규칙·not_found 규칙 |
| `checkapi/JdbcStockResolver.java` | `search()` ORDER BY, `resolve()` 정규화, not_found 제안 |
| `checkapi/StockMasterLoader.java` | 우선순위 적재 |
| `resources/schema.sql` | `stock_master.priority` 컬럼 |
| `resources/stock-priority.csv` | 신규 |
| `test/.../JdbcStockResolverTest.java` | 케이스 추가 |
| `test/.../EvalRunner.java` | `paramsExact` 정확일치 비교 추가 |
| `test/resources/eval-cases.yaml` | 케이스 추가 |

프론트 변경 없음. `MetricResult` 스키마·`clarify` SSE 계약 변경 없으므로 `docs/api.md` 갱신도 없다.
