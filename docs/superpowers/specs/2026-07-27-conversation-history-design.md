# 사이드바 대화 내역 — 설계

작성일: 2026-07-27

## 문제

사이드바에 "아직 대화 내역이 없습니다"만 뜨고, 새 대화를 시작하면 이전 대화가 사라진다.

원인은 셋이다.

1. `ChatPage`가 `AppLayout`에 `conversations`를 넘기지 않는다 — 목록은 항상 빈 배열이다.
2. `handleNewChat`이 떠나는 세션에 `endSession()` + `clearTranscript()`를 호출해 **이전 대화를 지운다**.
3. 대화 목록 인덱스가 없고, 사이드바 항목에 `onClick`이 없어 전환도 안 된다.

대화 자체는 이미 `kopilot.transcript.<sessionId>`로 localStorage에 저장되고 있고 새로고침 복원도 동작한다.
없는 것은 "어떤 대화가 있는지"를 아는 인덱스와, 그것을 화면에 잇는 배선이다.

## 목표

- 지난 대화가 사이드바에 날짜별로 쌓인다.
- 항목을 누르면 그 대화로 전환되고, 화면과 AI 맥락이 함께 따라온다.
- 항목을 지울 수 있다.

## 범위 밖

- 기기 간 동기화 (익명 세션이라 소유권을 가릴 수 없다)
- 제목 수정
- 사이드바의 지표 카탈로그·마이페이지·설정 링크 (별건)

## 선택한 접근

**localStorage 인덱스 + 기존 transcript 재사용.** 프론트만 바뀌고 백엔드·API 계약은 그대로다.

검토한 대안:

| 안 | 내용 | 기각 이유 |
|---|---|---|
| B | 서버에서 세션 목록을 조회하는 API 신설 | 익명 세션이라 "내 대화"를 가릴 수 없다. 백엔드와 `docs/api.md`까지 번진다 |
| C | 인덱스 없이 `kopilot.transcript.*` 키를 훑기 | 제목·시각 메타를 담을 데가 없어 매번 전체를 파싱해야 한다 |

## 설계

### 데이터

새 모듈 `src/lib/conversations.js`가 인덱스 하나만 책임진다.

- localStorage 키: `kopilot.conversations`
- 항목: `{ id, title, updatedAt }` — `id`는 sessionId이고, transcript 키와 같은 값이다
- `listConversations()` → `updatedAt` 내림차순 배열
- `touchConversation(id, title)` → 있으면 `title`·`updatedAt` 갱신, 없으면 추가
- `removeConversation(id)` → 인덱스에서 제거
- 상한 30개. 넘치면 오래된 것부터 인덱스와 transcript를 함께 정리한다

`session.js`에 전환용 `setSessionId(id)`를 더한다.

### 동작

- `ChatPage`는 `messages`가 바뀔 때 첫 user 메시지를 제목으로 삼아 `touchConversation`을 호출한다.
  질문이 하나도 없는 대화는 인덱스에 넣지 않는다 — 새 대화 버튼만 눌러도 빈 항목이 쌓이면 안 된다.
- `handleNewChat`에서 `endSession`·`clearTranscript` 호출을 뺀다. 새 sessionId를 발급하고 화면만 비운다.
  서버 컨텍스트(Redis)는 TTL 2시간 동안 남으므로, 그 안에 돌아오면 AI도 앞 대화를 기억한다.
- `handleSelectConversation(id)` → `setSessionId(id)`, `setMessages(loadTranscript(id))`, 사이드바 닫기.
- `handleDeleteConversation(id)` → `endSession(id)` + `clearTranscript(id)` + `removeConversation(id)`.
  보고 있던 대화를 지우면 새 대화로 넘어간다.

### UI

`SidebarNav`에 목록·날짜 그룹·제목 UI가 이미 있다. 붙일 것만 적는다.

- 항목 버튼에 `onSelect`, 현재 대화 강조(`activeId`)
- 호버 시 삭제 버튼
- `updatedAt` → "오늘 / 어제 / YYYY-MM-DD" 그룹 라벨. 표시 문제이므로 사이드바 안에 둔다

### 에러 처리

- localStorage 접근은 기존 `transcript.js`와 같이 try/catch로 감싼다. 인덱스를 못 읽으면 빈 목록으로
  계속 동작하고, 못 쓰면 조용히 넘어간다 — 보관 실패가 대화를 막지 않는다.
- 전환 대상 transcript가 비어 있으면 빈 화면으로 열고 인덱스에서 뺀다.

### 테스트

- `conversations.test.js` — 추가·갱신·정렬·상한·삭제
- `SidebarNav.test.jsx` — 목록 렌더, 선택 콜백, 삭제 콜백

## 트레이드오프

서버 컨텍스트를 즉시 지우지 않으므로 Redis에 세션이 TTL 2시간 동안 남는다.
로그인 없는 데모 규모에서는 무시할 만하고, 대화를 삭제하면 그때 함께 정리된다.
