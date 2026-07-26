import { describe, it, expect, vi, afterEach } from "vitest";
import { consumeSseBuffer, streamChat, endSession } from "../sse";

describe("consumeSseBuffer", () => {
  it("완결된 이벤트를 파싱하고 미완결 잔여분을 반환한다", () => {
    const onEvent = vi.fn();
    const buffer =
      'event: card\ndata: {"cardId":"abc"}\n\n' +
      'event: text\ndata: {"text":"해설"}\n\n' +
      "event: done\ndata: {"; // 미완결 조각
    const rest = consumeSseBuffer(buffer, onEvent);

    expect(onEvent).toHaveBeenCalledTimes(2);
    expect(onEvent).toHaveBeenNthCalledWith(1, "card", { cardId: "abc" });
    expect(onEvent).toHaveBeenNthCalledWith(2, "text", { text: "해설" });
    expect(rest).toBe("event: done\ndata: {");
  });

  it("data 없는 조각은 무시한다", () => {
    const onEvent = vi.fn();
    consumeSseBuffer("event: ping\n\n", onEvent);
    expect(onEvent).not.toHaveBeenCalled();
  });
});

function streamOf(text) {
  const encoder = new TextEncoder();
  let sent = false;
  return {
    getReader: () => ({
      read: async () =>
        sent ? { done: true } : ((sent = true), { done: false, value: encoder.encode(text) }),
    }),
  };
}

describe("streamChat", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("이벤트를 순서대로 전달한다", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => ({
        ok: true,
        body: streamOf('event: card\ndata: {"cardId":"c1"}\n\nevent: done\ndata: {}\n\n'),
      })),
    );
    const onEvent = vi.fn();

    await streamChat("sess-1", "질문", onEvent);

    expect(onEvent.mock.calls.map(([event]) => event)).toEqual(["card", "done"]);
  });

  it("실패를 mock으로 가리지 않고 던진다", async () => {
    // 조작된 수치를 진짜처럼 렌더하면 안 된다 — 계산 신뢰가 이 제품의 전부다
    vi.stubGlobal("fetch", vi.fn(async () => ({ ok: false, status: 400, body: null })));

    await expect(streamChat("sess-1", "질문", vi.fn())).rejects.toThrow("400");
  });

  it("네트워크 오류도 그대로 던진다", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => { throw new TypeError("network"); }));

    await expect(streamChat("sess-1", "질문", vi.fn())).rejects.toThrow();
  });
});

describe("endSession", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("서버 컨텍스트 삭제를 요청한다", async () => {
    const fetchMock = vi.fn(async () => ({ ok: true }));
    vi.stubGlobal("fetch", fetchMock);

    await endSession("sess-1");

    expect(fetchMock).toHaveBeenCalledWith("/api/chat/sess-1", { method: "DELETE" });
  });

  it("삭제 실패는 새 대화를 막지 않는다", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => { throw new TypeError("network"); }));

    await expect(endSession("sess-1")).resolves.toBeUndefined();
  });
});
