const KEY = "kopilot.sessionId";

/**
 * crypto.randomUUID()는 보안 컨텍스트(HTTPS/localhost)에서만 존재한다.
 * HTTP로 서빙되는 배포 환경에서도 세션 발급이 죽지 않도록 getRandomValues 기반 폴백을 둔다.
 */
function randomUUID() {
  if (typeof crypto !== "undefined" && crypto.randomUUID) {
    return crypto.randomUUID();
  }
  const bytes = crypto.getRandomValues(new Uint8Array(16));
  bytes[6] = (bytes[6] & 0x0f) | 0x40; // version 4
  bytes[8] = (bytes[8] & 0x3f) | 0x80; // variant
  const hex = [...bytes].map((b) => b.toString(16).padStart(2, "0"));
  return [
    hex.slice(0, 4).join(""),
    hex.slice(4, 6).join(""),
    hex.slice(6, 8).join(""),
    hex.slice(8, 10).join(""),
    hex.slice(10, 16).join(""),
  ].join("-");
}

/**
 * 로그인 없는 MVP의 대화 컨텍스트 키. UUID를 localStorage에 보관해 새로고침에도 이어지게 한다.
 */
export function getSessionId() {
  const saved = localStorage.getItem(KEY);
  if (saved) return saved;
  const fresh = randomUUID();
  localStorage.setItem(KEY, fresh);
  return fresh;
}

/** 데모 리셋용 — 새 대화 시작 */
export function resetSessionId() {
  const fresh = randomUUID();
  localStorage.setItem(KEY, fresh);
  return fresh;
}

/** 사이드바에서 지난 대화를 골랐을 때 — 그 대화의 세션으로 되돌아간다 */
export function setSessionId(sessionId) {
  localStorage.setItem(KEY, sessionId);
  return sessionId;
}
