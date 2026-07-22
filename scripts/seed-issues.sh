#!/usr/bin/env bash
# docs/plan.md의 Task 1~20을 GitHub 이슈로 만든다.
# 이미 끝난 Task 1·2는 생성 직후 닫는다.
#
#   ./scripts/setup-labels.sh   # 라벨 먼저
#   ./scripts/seed-issues.sh
#
# 저장소에 이슈·PR이 하나도 없는 상태에서 실행하면 이슈 번호가 Task 번호와 같아진다.
set -euo pipefail

REPO="${REPO:-CHECK-KoPilot/CHECK-KoPilot}"
PLAN="https://github.com/${REPO}/blob/main/docs/plan.md"

# 번호|제목|area|prio|선행 Task|목표
TASKS=$(cat <<'EOF'
1|프로젝트 스캐폴딩 (git, Gradle, MySQL·Redis, 헬스체크)|infra|P0||백엔드·프론트 프로젝트가 뜨고 헬스체크가 200을 준다
2|CHECK API 클라이언트 (도메인 레코드, REST 구현, 픽스처 구현)|check-api|P0|1|CHECK API를 Java에서 호출하고 결과를 레코드로 받는다
3|응답 캐시(Redis TTL) + 장애 폴백(MySQL) + 종목 마스터(이름→코드 검색)|backend|P0|2|같은 질의를 반복해도 CHECK API를 다시 때리지 않고, 종목명으로 코드를 찾는다
4|도메인 모델(카드 스키마)과 계산 유틸|backend|P0|3|답변 카드 JSON 스키마와 공용 계산 함수가 정의된다
5|실행기 프레임워크 + 지표① 수익률 갭|backend|P0|4|지표 실행기 구조가 잡히고 수익률 갭 카드가 나온다
6|지표② 변동성 비교 + 지표⑥ 기간 시세 요약|backend|P0|5|변동성 비교와 기간 시세 요약 카드가 나온다
7|지표③ ETF 괴리율 + 지표④ 이동평균 이격도 + 지표⑤ 상대수익률 랭킹|backend|P0|5|나머지 지표 3종 카드가 나온다
8|가이드 모듈 (F코드 역인덱스 검색 + 레시피 tool 2종)|backend|P1|5|카탈로그 밖 질문에 레시피 카드로 답한다
9|카드 저장소 + xlsx 내보내기|backend|P0|8|카드를 저장하고 3시트 xlsx로 내려받는다
10|Tool 디스패처 (tool 호출 → 실행기/가이드 라우팅 + SSE 이벤트 생성)|backend|P0|9|tool 호출이 실행기·가이드로 라우팅되고 SSE 이벤트가 만들어진다
11|수요조사 적재 (가이드 카드 발생 + 추가 요청 버튼)|backend|P2|10|카탈로그 밖 질문과 추가 요청이 기록된다
12|익명 세션 + Redis 대화 컨텍스트 저장소|backend|P0|3|브라우저 UUID로 세션이 격리되고 대화 맥락이 유지된다
13|chat 모듈 — Spring AI 수동 tool 루프 + SSE 엔드포인트 + 시스템 프롬프트 + 로깅|llm|P0|11, 12|자연어 질문이 tool 선택을 거쳐 SSE로 카드까지 흘러간다
14|tool 선택 평가셋 (의도 인식 정확도 %)|llm|P1|13|평가셋을 돌려 의도 인식 정확도를 숫자로 낸다
15|Admin 수요조사 API (토큰 접근 제어)|backend|P2|11|토큰으로 보호된 수요조사 조회 API가 동작한다
16|프론트엔드 스캐폴딩 + 채팅 UI + SSE 클라이언트|frontend|P0|13|채팅 화면에서 질문을 보내고 SSE 응답을 받는다
17|지표 답변 카드 (핵심 수치 · Recharts 차트 · 근거 패널 · xlsx 다운로드)|frontend|P0|16|지표 카드가 수치·차트·근거와 함께 렌더된다
18|가이드(레시피) 카드 + 종목 되묻기 칩|frontend|P1|17|가이드 카드와 되묻기 칩이 렌더된다
19|Admin 수요조사 화면 (프론트)|frontend|P2|18|수요조사 목록을 화면에서 본다
20|E2E 데모 준비 (캐시 워밍업, README, 리허설)|docs|P0|19|데모 시나리오가 끊김 없이 재생된다
EOF
)

while IFS='|' read -r num title area prio deps goal; do
  [ -z "$num" ] && continue

  if [ -n "$deps" ]; then
    dep_line=$(echo "$deps" | sed 's/[0-9]\+/#&/g')
  else
    dep_line="없음"
  fi

  body=$(cat <<EOF
## 목표

$goal

## 대상 파일

[plan.md의 Task $num]($PLAN) 본문 참고.

## 완료 조건

- [ ] plan.md Task $num의 Step을 모두 구현
- [ ] 테스트 통과
- [ ] PR 머지

## 선행 Task

$dep_line

## 참고 문서

- [docs/plan.md]($PLAN) — Task $num
- [docs/spec.md](https://github.com/$REPO/blob/main/docs/spec.md)
EOF
)

  if [ "$area" = "docs" ]; then type="chore"; else type="feat"; fi

  url=$(gh issue create -R "$REPO" \
    --title "[Task $num] $title" \
    --body "$body" \
    --label "area/$area" \
    --label "type/$type" \
    --label "prio/$prio")

  echo "생성: $url"

  # Task 1·2는 이미 완료
  if [ "$num" = "1" ] || [ "$num" = "2" ]; then
    gh issue close "$url" -R "$REPO" --comment "구현 완료 (저장소에 반영됨)." >/dev/null
    echo "  → 완료 처리"
  fi
done <<< "$TASKS"

echo
echo "완료. 남은 이슈:"
gh issue list -R "$REPO" -L 30
