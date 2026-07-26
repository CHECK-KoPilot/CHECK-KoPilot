import { describe, it, expect, vi } from "vitest";
import { consumeSseBuffer } from "../sse";

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
