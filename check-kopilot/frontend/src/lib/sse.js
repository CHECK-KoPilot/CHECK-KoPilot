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
 * 임시: 백엔드가 배포되기 전까지는 요청이 실패하면 mock 응답으로 대체한다
 * (배포 후 이 폴백만 지우면 됨 — data/mockChat.js 참조).
 */
export async function streamChat(sessionId, message, onEvent) {
  try {
    const res = await fetch(`/api/chat/${sessionId}`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ message }),
    });
    if (!res.ok || !res.body) throw new Error(`chat 요청 실패: ${res.status}`);

    const reader = res.body.getReader();
    const decoder = new TextDecoder();
    let buffer = "";
    for (;;) {
      const { done, value } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });
      buffer = consumeSseBuffer(buffer, onEvent);
    }
  } catch {
    const { mockStreamChat } = await import("../data/mockChat");
    await mockStreamChat(message, onEvent);
  }
}
