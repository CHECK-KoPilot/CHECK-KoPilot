#!/usr/bin/env bash
# 저장소 라벨을 CONTRIBUTING.md의 3축 체계로 맞춘다.
# 기본 라벨(bug, enhancement 등)은 지우고 area/ · type/ · prio/ 로 통일한다.
#
#   ./scripts/setup-labels.sh
set -euo pipefail

REPO="${REPO:-CHECK-KoPilot/CHECK-KoPilot}"

# GitHub 기본 라벨 제거
for l in bug documentation duplicate enhancement "good first issue" "help wanted" invalid question wontfix; do
  gh label delete "$l" -R "$REPO" --yes 2>/dev/null || true
done

create() { # name color description
  gh label create "$1" -R "$REPO" --color "$2" --description "$3" --force
}

# area/ — 담당 영역
create "area/backend"    "1d76db" "Spring Boot 백엔드"
create "area/frontend"   "0e8a16" "React 프론트엔드"
create "area/infra"      "5319e7" "Gradle, Docker, MySQL, Redis, 배포"
create "area/llm"        "b60205" "Spring AI, tool calling, 프롬프트"
create "area/check-api"  "fbca04" "CHECK API 연동 및 조사"
create "area/docs"       "0075ca" "문서"

# type/ — 작업 성격 (커밋 type과 동일)
create "type/feat"       "a2eeef" "기능 추가"
create "type/fix"        "d73a4a" "버그 수정"
create "type/docs"       "c5def5" "문서 변경"
create "type/chore"      "cfd3d7" "빌드, 설정, 의존성"
create "type/test"       "bfd4f2" "테스트 추가·수정"

# prio/ — 우선순위
create "prio/P0"         "b60205" "데모 필수"
create "prio/P1"         "e99695" "있으면 좋음"
create "prio/P2"         "f9d0c4" "여유되면"

echo "라벨 정리 완료:"
gh label list -R "$REPO"
