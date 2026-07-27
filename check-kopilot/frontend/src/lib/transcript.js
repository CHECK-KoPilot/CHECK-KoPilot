const PREFIX = "kopilot.transcript.";

/**
 * 화면에 그려진 대화를 세션 단위로 보관한다.
 *
 * 서버는 세션 컨텍스트를 Redis에 들고 있어서 새로고침해도 AI는 앞 대화를 기억하는데,
 * 화면만 비어 있으면 사용자는 맥락이 끊긴 줄 알고 조건을 다시 말하게 된다.
 * 세션 ID와 같은 수명(localStorage)을 줘서 둘이 어긋나지 않게 한다.
 *
 * 서버 컨텍스트는 TTL(기본 2시간)로 먼저 만료될 수 있다. 그때는 화면에 지난 대화가 보이지만
 * AI는 기억하지 못한다 — 빈 화면보다는 낫고, "새 대화"로 양쪽을 함께 정리할 수 있다.
 */
export function loadTranscript(sessionId) {
  try {
    const saved = localStorage.getItem(PREFIX + sessionId);
    const parsed = saved ? JSON.parse(saved) : [];
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return []; // 포맷이 바뀌었으면 화면만 비우고 계속 동작한다
  }
}

export function saveTranscript(sessionId, messages) {
  try {
    localStorage.setItem(PREFIX + sessionId, JSON.stringify(messages));
  } catch {
    // 용량 초과 등 — 보관 실패가 대화를 막지는 않는다
  }
}

export function clearTranscript(sessionId) {
  try {
    localStorage.removeItem(PREFIX + sessionId);
  } catch {
    // 무시
  }
}
