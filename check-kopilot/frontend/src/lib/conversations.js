import { clearTranscript } from "./transcript";

const KEY = "kopilot.conversations";

/** 인덱스가 무한정 자라지 않게 하는 상한. 넘치면 오래된 대화부터 본문까지 함께 지운다 */
const MAX = 30;

/**
 * 어떤 대화가 있는지만 아는 인덱스.
 *
 * 대화 본문은 이미 transcript.js가 세션 단위로 보관한다(`kopilot.transcript.<id>`).
 * 여기 있는 건 목록에 필요한 메타뿐이고, `id`는 sessionId라 둘이 같은 키로 이어진다.
 * 서버(Redis) 컨텍스트도 같은 id를 쓰므로, 대화를 지울 때 셋을 함께 정리할 수 있다.
 */
function read() {
  try {
    const saved = JSON.parse(localStorage.getItem(KEY) ?? "[]");
    return Array.isArray(saved) ? saved.filter((c) => c && c.id) : [];
  } catch {
    return []; // 포맷이 바뀌었으면 목록만 비우고 계속 동작한다
  }
}

function write(conversations) {
  try {
    localStorage.setItem(KEY, JSON.stringify(conversations));
  } catch {
    // 용량 초과 등 — 보관 실패가 대화를 막지는 않는다
  }
}

/** 최근에 쓴 대화가 위로 */
export function listConversations() {
  return read().sort((a, b) => (b.updatedAt ?? 0) - (a.updatedAt ?? 0));
}

/**
 * 대화를 인덱스에 올리고 마지막 질문 시각을 지금으로 올린다. 제목은 그 대화의 첫 질문이다.
 * 목록 순서를 정하는 값이므로 **질문이 오갈 때만** 부른다 — 여는 것만으로 순서가 바뀌면
 * 사용자가 훑던 기준이 사라진다(그 경우는 `ensureConversation`).
 *
 * 상한을 넘으면 가장 오래된 대화를 본문까지 지운다 — 인덱스에서만 빼면 transcript가 남아 샌다.
 */
export function touchConversation(id, title) {
  const others = read().filter((c) => c.id !== id);
  const next = [{ id, title, updatedAt: Date.now() }, ...others].sort(
    (a, b) => (b.updatedAt ?? 0) - (a.updatedAt ?? 0)
  );

  for (const dropped of next.slice(MAX)) {
    clearTranscript(dropped.id);
  }
  write(next.slice(0, MAX));
  return next.slice(0, MAX);
}

/**
 * 인덱스에 없으면 올리고, 이미 있으면 그대로 둔다.
 * 대화를 열어보기만 한 것으로 목록 순서가 바뀌면 안 되므로 `updatedAt`을 건드리지 않는다.
 */
export function ensureConversation(id, title) {
  if (read().some((c) => c.id === id)) return listConversations();
  return touchConversation(id, title);
}

export function removeConversation(id) {
  const next = read().filter((c) => c.id !== id);
  write(next);
  return next;
}
