<div align="center">

# CHECK Kopilot

**자연어로 코스콤 CHECK API 금융 데이터를 조회 및 가공하는 AI 데이터 어시스턴트**

수치는 LLM이 아니라 백엔드가 계산하고, 그 근거를 항상 공개합니다.

<br/>

<img width="512" height="232" alt="image" src="https://github.com/user-attachments/assets/b0202177-75c9-4c0b-b010-bfd5694846b7" />


</div>

---

## 한눈에 보기

> 💬 **"삼성전자랑 코스피, 최근 한 달 수익률 갭 알려줘"**

| | |
|---|---|
| 🧠 **LLM은 해석만** | 지표 tool 선택 · 파라미터 추출 · 해설 텍스트까지만 담당 |
| ⚙️ **계산은 백엔드가** | CHECK API 호출과 모든 수치 계산은 백엔드 서버 로직이 수행 |
| 🔍 **근거는 항상 공개** | 호출 API · 원본 수치 · 공식 · 중간 계산값을 카드에 함께 제공 |
| 📊 **바로 쓸 수 있게** | 차트 렌더링 + 3시트 xlsx 다운로드 |

</br>

## 왜 만들었나

**① 원하는 지표가 화면에 없다** - 수익률 갭, ETF 괴리율, 변동성 비교 같은 2차 가공 데이터는 CHECK 단말기와 HTS/MTS의 정형 화면에 없습니다. 결국 원시 데이터를 내려받아 엑셀로 가공하거나 IT 부서에 요청해야 합니다.

**② 자연어로 가공까지 해주는 서비스가 없다** - 해외에는 BloombergGPT, Morgan Stanley AI Assistant가 있지만, 국내 자본시장 데이터를 자연어로 조회하고 2차 가공까지 수행하는 서비스는 아직 없습니다.

**③ 그런데 LLM에 계산을 맡길 수는 없다** - 금융에서 환각은 곧 잘못된 투자 판단입니다. 「금융분야 AI 가이드라인」(2026.06 시행) 역시 보조수단성과 신뢰성(설명가능성)을 요구합니다.

> 그래서 **LLM은 자연어 해석까지만**, **계산은 백엔드가**, **근거는 항상 함께** 제시하는 구조를 택했습니다.

</br>

## 주요 기능

### ① 자연어 데이터 조회 (NL2API)

API 구조나 파라미터를 몰라도 평소 쓰는 말로 물어보면 됩니다. LLM이 질문에서 지표와 종목, 기간을 뽑아내면 백엔드가 종목 마스터에서 종목명을 코드로 바꾼 뒤 CHECK API를 호출합니다.

<img width="1269" height="453" alt="image" src="https://github.com/user-attachments/assets/72f6b98b-c113-4534-a034-d9ed7c338aca" />
</br></br>

### ② 지표 답변 카드

카드 위쪽에는 백엔드가 계산한 핵심 수치가, 아래쪽에는 Recharts 차트(line, bar)가 붙습니다. 카드에 찍히는 숫자는 전부 백엔드 JSON을 그대로 렌더한 값입니다. LLM이 쓴 해설 문구가 어떻든 수치는 항상 검증된 값으로 남습니다.

<img width="1440" height="1226" alt="image" src="https://github.com/user-attachments/assets/b7a865d7-e31c-46e6-b0a1-e03a9dd43bcf" />
</br></br>

### ③ 근거 패널

근거 패널을 펼치면 호출한 CHECK API와 명세 링크, 원본 시세 데이터, 적용한 공식, 중간 계산값이 모두 나옵니다. 카드에 적힌 숫자를 직접 따라 계산해 확인하실 수 있습니다.

<img width="1378" height="1232" alt="image" src="https://github.com/user-attachments/assets/8952afac-f196-451c-84aa-5645a42f8774" />

</br></br>


### ④ 종목 되묻기

"삼성"처럼 종목이 특정되지 않으면 임의로 고르지 않습니다. 삼성전자, 삼성전기, 삼성SDS를 칩 버튼으로 띄우고, 하나를 누르면 질문을 다시 쓸 필요 없이 그대로 답변으로 이어집니다.

<img width="1448" height="502" alt="image" src="https://github.com/user-attachments/assets/d63189a3-199e-4d27-854e-594bd2a9352c" />
</br></br>


### ⑤ 가이드(레시피) 카드

카탈로그에 없는 지표를 물어보면 답할 수 없다고 끝내지 않고 가이드 모드로 넘어갑니다. 어떤 CHECK API가 필요한지 명세 링크와 함께 알려주고, 호출 파라미터와 조합해서 계산하는 방법까지 레시피로 정리해 줍니다. 직접 구현하시거나 개발 부서에 그대로 전달하시면 됩니다. 카드 아래 "카탈로그 추가 요청" 버튼을 누르면 어떤 지표를 먼저 만들어야 할지 판단할 데이터로 쌓입니다.
<img width="2814" height="2094" alt="image" src="https://github.com/user-attachments/assets/85ac0055-6a05-43eb-acc0-6f3e001dcbb5" />
</br></br>


### ⑥ Excel(xlsx) 내보내기

카드마다 Apache POI로 3시트 엑셀 파일을 만듭니다. 결과 요약, 원본 데이터, 계산 과정 순서입니다. 매번 손으로 하던 엑셀 작업이 버튼 하나로 끝납니다.
<img width="746" height="386" alt="image" src="https://github.com/user-attachments/assets/092e4b76-07d7-49e8-bfce-a922bbe3edf0" />
</br></br>

### ⑦ 컴플라이언스 가드레일

투자 판단이나 권유, 전망은 만들지 않습니다. 대신 사실에 기반한 지표를 제안하는 쪽으로 돌립니다. 화면 아래에는 고지 문구가 항상 붙어 있습니다.
<img width="1456" height="310" alt="image" src="https://github.com/user-attachments/assets/f2b47290-e408-4704-a418-1673cffeb5bb" />
</br></br>

### ⑧ 튜토리얼

처음 들어온 분을 위해 튜토리얼를 준비했습니다. 
<img width="1958" height="1074" alt="image" src="https://github.com/user-attachments/assets/b0d3d63d-c157-4032-9a7c-927ef71d4230" />




</br></br>

## 제공 지표 카탈로그 7종

| # | 지표 | 질문 예시 | 계산 |
|---|---|---|---|
| 1 | 수익률 갭 | "삼성전자랑 코스피, 최근 한 달 수익률 갭" | 두 대상의 기간 수익률 차이 |
| 2 | 변동성 비교 | "에코프로랑 에코프로비엠 변동성 비교" | 일간수익률 표준편차, 연율화 |
| 3 | 괴리율 | "TIGER 미국S&P500 괴리율" | ETF 시장가 vs NAV(iNAV) |
| 4 | 이동평균 이격도 | "카카오 20일선 이격도" | 현재가 / N일 이동평균 − 1 |
| 5 | 상대수익률 랭킹 | "에코프로, 엘앤에프, 포스코퓨처엠 3개월 수익률 순위" | 복수 종목 기간 수익률 정렬 |
| 6 | 기간 시세 요약 | "현대차 올해 최고가·최저가·수익률" | 기간 OHLC 집계 |
| 7 | 누적수익률 | "네이버 최근 3개월 누적수익률 차트" | 일별 누적수익률과 기간 최고·최저 |


</br>

## 시스템 아키텍처
</br>
<img width="1085" height="702" alt="image" src="https://github.com/user-attachments/assets/42fdc70f-2480-4e31-afe5-1e675ed22e0e" />


<details>
<summary><b>요청 처리 흐름 (시퀀스 다이어그램)</b></summary>

<br/>

사용자 질문은 **① 지표 답변 카드 / ② 종목 되묻기 / ③ 가이드 레시피** 세 갈래로 분기하며, 어떤 흐름에서도 수치 계산은 백엔드가 수행합니다.

```mermaid
sequenceDiagram
    autonumber
    participant U as 사용자
    participant F as React SPA
    participant C as chat 모듈
    participant L as OpenAI gpt-4o
    participant X as 지표 실행기
    participant K as CHECK API

    U->>F: 자연어 질문
    F->>C: POST /api/chat/{sessionId} (SSE)
    C->>L: 대화 + tool 스키마 (자동 실행 off)
    L-->>C: tool 선택 + 파라미터

    alt 지표 tool 매칭
        C->>X: 디스패치
        X->>K: 시세/NAV 조회 (Redis 캐시 → 실패 시 MySQL 폴백)
        K-->>X: 원본 시계열
        X->>X: Java 계산 + 근거 생성
        X-->>C: MetricResult
        C-->>F: event: card
    else 종목명 다건 매칭
        X-->>C: AmbiguousStockException(후보 목록)
        C-->>F: event: clarify
    else 카탈로그 밖 질문
        C->>C: guide 레시피 생성
        C-->>F: event: guide
    end

    C->>L: tool 결과 전달
    L-->>C: 해설 텍스트 (수치 아님)
    C-->>F: event: text → event: done
```

**에러·가드레일** — 실행기는 검증만 하고 되묻기는 LLM에 맡깁니다. `MetricException`(`PERIOD_INVERTED`, `DATA_INSUFFICIENT` 등)과 `AmbiguousStockException`을 던지면 디스패처가 구조화 에러로 LLM에 되돌려주고 LLM이 자연어로 되묻습니다. **백엔드는 잘못된 계산 결과를 내지 않습니다.**

</details>

</br></br>

## 인프라 아키텍처

네이버 클라우드 플랫폼(FIN 리전)에 Terraform으로 VPC·NKS·DB를 프로비저닝하고, GitHub Actions self-hosted runner가 이미지를 NCR에 푸시한 뒤 매니페스트 태그를 갱신하는 **GitOps** 방식으로 배포합니다.
</br></br>
<img width="1490" height="895" alt="image" src="https://github.com/user-attachments/assets/4f9aeb6a-a97c-44ee-8f39-c63cff269965" />


</br></br>

## 기술스택

| 분류 | 기술 스택 |
|------|----------|
| 공통 | ![](https://img.shields.io/badge/Java%2021-007396?logo=openjdk&logoColor=white) ![](https://img.shields.io/badge/Gradle-02303A?logo=gradle&logoColor=white) ![](https://img.shields.io/badge/Node.js%2020-5FA04E?logo=nodedotjs&logoColor=white) ![](https://img.shields.io/badge/NPM-%23CB3837.svg?logo=npm&logoColor=white) |
| FE | ![](https://img.shields.io/badge/React%2019-61DAFB?logo=react&logoColor=black) ![](https://img.shields.io/badge/Vite%208-646CFF?logo=vite&logoColor=white) ![](https://img.shields.io/badge/Tailwind%20CSS%204-06B6D4?logo=tailwindcss&logoColor=white) ![](https://img.shields.io/badge/Recharts%203-FF6384?logo=chartdotjs&logoColor=white) ![](https://img.shields.io/badge/React%20Router%207-CA4245?logo=reactrouter&logoColor=white) ![](https://img.shields.io/badge/PWA-5A0FC8?logo=pwa&logoColor=white) |
| BE | ![](https://img.shields.io/badge/Spring%20Boot%203.5-6DB33F?logo=springboot&logoColor=white) ![](https://img.shields.io/badge/Spring%20Web%20%C2%B7%20JDBC%20%C2%B7%20Data%20Redis-6DB33F?logo=spring&logoColor=white) ![](https://img.shields.io/badge/Spring%20Actuator-6DB33F?logo=spring&logoColor=white) ![](https://img.shields.io/badge/Apache%20POI%205.3-D22128?logo=apache&logoColor=white) ![](https://img.shields.io/badge/Jackson-4B8BBE?logo=json&logoColor=white) |
| AI | ![](https://img.shields.io/badge/OpenAI%20gpt--4o-412991?logo=openai&logoColor=white) ![](https://img.shields.io/badge/Spring%20AI%201.0.0-6DB33F?logo=spring&logoColor=white) ![](https://img.shields.io/badge/Tool%20Calling-1A7F64?logo=openai&logoColor=white) |
| Database | ![](https://img.shields.io/badge/MySQL%208.4-4479A1?logo=mysql&logoColor=white) ![](https://img.shields.io/badge/Redis%207-%23DD0031.svg?logo=redis&logoColor=white) |
| External API | ![](https://img.shields.io/badge/KOSCOM%20CHECK%20API-0B4DA2?logoColor=white) ![](https://img.shields.io/badge/F%EC%BD%94%EB%93%9C%201%2C841-4A5568?logoColor=white) ![](https://img.shields.io/badge/Endpoint%20776-4A5568?logoColor=white) |
| Test | ![](https://img.shields.io/badge/-JUnit%205-%2325A162?logo=junit5&logoColor=white) ![](https://img.shields.io/badge/Vitest-6E9F18?logo=vitest&logoColor=white) ![](https://img.shields.io/badge/Testing%20Library-E33332?logo=testinglibrary&logoColor=white) ![](https://img.shields.io/badge/oxlint-2E2E2E?logoColor=white) |
| Infrastructure | ![](https://img.shields.io/badge/Docker-2496ED?logo=docker&logoColor=white) ![](https://img.shields.io/badge/Kubernetes%20(NKS)-326CE5?logo=kubernetes&logoColor=white) ![](https://img.shields.io/badge/Terraform-7B42BC?logo=terraform&logoColor=white) ![](https://img.shields.io/badge/GitHub%20Actions-%232671E5.svg?logo=githubactions&logoColor=white) ![](https://img.shields.io/badge/Naver%20Cloud%20Platform-03C75A?logo=naver&logoColor=ffffff) |
| Collaboration Tools | ![](https://img.shields.io/badge/-GitHub-181717?logo=github&logoColor=white) ![](https://img.shields.io/badge/-Notion-000000?logo=notion&logoColor=white) ![](https://img.shields.io/badge/Figma-%23F24E1E.svg?logo=figma&logoColor=white) ![](https://img.shields.io/badge/-Slack-4A154B?logo=slack&logoColor=white) |
</br>

## 프로젝트 구조

```
check-kopilot/
├─ backend/                   Spring Boot — 패키지 = 모듈
│  └─ .../kopilot/
│     ├─ checkapi/            CHECK 클라이언트 · 캐시/폴백 · 종목명→코드
│     ├─ domain/              카드 스키마(MetricResult) · 계산 유틸 · 예외
│     ├─ catalog/             지표 실행기 7종 + CatalogService
│     ├─ export/              CardStore · POI xlsx
│     ├─ chat/                수동 tool 루프 · 디스패처 · SSE · 익명 세션
│     ├─ guide/               F코드 사전 · API 역인덱스 · 레시피 생성
│     └─ demand/              추가요청 적재 · Admin 집계
├─ frontend/                  React 19 + Vite + Tailwind + Recharts
│  └─ src/
│     ├─ components/chat/cards/   IndicatorAnswerCard · EvidencePanel · ChartPanel
│     │                            GuideRecipeCard · ClarificationCard · KeyMetricsPanel
│     ├─ components/tour/         제품 투어
│     ├─ admin/                   Admin 수요 대시보드
│     └─ lib/                     SSE 클라이언트 · 세션 · 대화 저장 · 컴플라이언스
├─ infra/terraform/           NCP VPC · NKS · DB · ACG
├─ k8s/                       backend / frontend Deployment · Service
└─ docker-compose.yml         MySQL 8 · Redis 7
```

</br>

## 팀 소개

|                                       전진혁                                       |                                     심재성                                      |                                    박준상                                    |                                    최예빈                                    |                                    이승형                                    |
|:-------------------------------------------------------------------------------:|:------------------------------------------------------------------------------:|:---------------------------------------------------------------------------:|:---------------------------------------------------------------------------:|:---------------------------------------------------------------------------:|
| <img src="https://github.com/Jeon-Jinhyeok.png" width="100" height="100"> | <img src="https://github.com/simjaesung.png" width="100" height="100"> | <img src="https://github.com/ehgkals.png" width="100" height="100"> | <img src="https://github.com/beenvyn.png" width="100" height="100"> | <img src="https://github.com/SHL0915.png" width="100" height="100"> |
|          [@Jeon-Jinhyeok](https://github.com/Jeon-Jinhyeok)          |          [@simjaesung](https://github.com/simjaesung)          |            [@ehgkals](https://github.com/ehgkals)            |            [@beenvyn](https://github.com/beenvyn)            |            [@SHL0915](https://github.com/SHL0915)            |
|                                     AI 개발                                      |                                   Back-end                                    |                                  Back-end                                   |                                  Front-end                                  |                                 Cloud Infra                                 |
