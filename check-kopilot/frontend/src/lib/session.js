const KEY = "kopilot.sessionId";

/**
 * 로그인 없는 MVP의 대화 컨텍스트 키. UUID를 localStorage에 보관해 새로고침에도 이어지게 한다.
 */
export function getSessionId() {
  const saved = localStorage.getItem(KEY);
  if (saved) return saved;
  const fresh = crypto.randomUUID();
  localStorage.setItem(KEY, fresh);
  return fresh;
}

/** 데모 리셋용 — 새 대화 시작 */
export function resetSessionId() {
  const fresh = crypto.randomUUID();
  localStorage.setItem(KEY, fresh);
  return fresh;
}

/** 사이드바에서 지난 대화를 골랐을 때 — 그 대화의 세션으로 되돌아간다 */
export function setSessionId(sessionId) {
  localStorage.setItem(KEY, sessionId);
  return sessionId;
}
