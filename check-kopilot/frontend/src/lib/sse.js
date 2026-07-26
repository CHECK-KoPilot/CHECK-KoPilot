/**
 * SSE 텍스트 버퍼에서 완결된 이벤트(빈 줄로 구분)를 파싱해 콜백으로 전달하고,
 * 아직 끝나지 않은 마지막 조각은 반환해 다음 청크와 이어붙일 수 있게 한다.
 */
export function consumeSseBuffer(buffer, onEvent) {
  const parts = buffer.split("\n\n");
  const rest = parts.pop() ?? "";
  for (const part of parts) {
    let event = "message";
    let data = "";
    for (const line of part.split("\n")) {
      if (line.startsWith("event:")) event = line.slice(6).trim();
      else if (line.startsWith("data:")) data += line.slice(5).trim();
    }
    if (data) onEvent(event, JSON.parse(data));
  }
  return rest;
}

/**
 * POST 기반 SSE 스트림 소비. EventSource는 GET 전용이라 fetch 스트림을 직접 읽는다.
 *
 * 실패는 삼키지 않고 그대로 던진다. 예전에는 백엔드가 없어 mock 카드로 폴백했지만,
 * 그 상태로는 실제 오류가 조작된 수치를 진짜처럼 렌더한다 — 계산 신뢰가 이 제품의 전부다.
 * 호출부(ChatPage)가 오류 메시지로 표시한다.
 */
export async function streamChat(sessionId, message, onEvent) {
  const res = await fetch(`/api/chat/${sessionId}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ message }),
  });
  if (!res.ok || !res.body) {
    throw new Error(`chat 요청 실패: ${res.status}`);
  }

  const reader = res.body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";
  for (;;) {
    const { done, value } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });
    buffer = consumeSseBuffer(buffer, onEvent);
  }
}

/**
 * "새 대화" — 서버가 들고 있는 대화 컨텍스트를 버린다.
 * 실패해도 새 대화 자체는 진행한다(클라이언트가 새 세션 UUID를 쓰므로 맥락은 어차피 끊긴다).
 */
export async function endSession(sessionId) {
  try {
    await fetch(`/api/chat/${sessionId}`, { method: "DELETE" });
  } catch {
    // 서버 정리는 실패해도 TTL이 결국 회수한다
  }
}
