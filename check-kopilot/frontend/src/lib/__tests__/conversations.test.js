import { describe, it, expect, beforeEach, vi } from "vitest";
import {
  listConversations,
  touchConversation,
  removeConversation,
} from "../conversations";
import { loadTranscript, saveTranscript } from "../transcript";

describe("conversations", () => {
  beforeEach(() => localStorage.clear());

  it("최근에 쓴 대화를 위로 올린다", () => {
    vi.spyOn(Date, "now").mockReturnValue(1000);
    touchConversation("sess-1", "삼성전자 수익률");
    Date.now.mockReturnValue(2000);
    touchConversation("sess-2", "코스피 변동성");

    expect(listConversations().map((c) => c.id)).toEqual(["sess-2", "sess-1"]);
    vi.restoreAllMocks();
  });

  it("같은 대화를 다시 쓰면 항목이 늘지 않고 제목과 시각만 갱신된다", () => {
    touchConversation("sess-1", "첫 질문");
    touchConversation("sess-1", "첫 질문");

    const list = listConversations();
    expect(list).toHaveLength(1);
    expect(list[0].title).toBe("첫 질문");
  });

  it("상한을 넘으면 가장 오래된 대화를 본문까지 지운다", () => {
    // 인덱스에서만 빼면 transcript가 localStorage에 남아 샌다
    vi.spyOn(Date, "now").mockReturnValue(0);
    for (let i = 0; i < 31; i++) {
      Date.now.mockReturnValue(i);
      saveTranscript(`sess-${i}`, [{ id: `u-${i}`, type: "user", text: "질문" }]);
      touchConversation(`sess-${i}`, `대화 ${i}`);
    }

    const list = listConversations();
    expect(list).toHaveLength(30);
    expect(list.map((c) => c.id)).not.toContain("sess-0");
    expect(loadTranscript("sess-0")).toEqual([]);
    expect(loadTranscript("sess-1")).toHaveLength(1);
    vi.restoreAllMocks();
  });

  it("삭제하면 목록에서 빠진다", () => {
    touchConversation("sess-1", "A");
    touchConversation("sess-2", "B");

    removeConversation("sess-1");

    expect(listConversations().map((c) => c.id)).toEqual(["sess-2"]);
  });

  it("깨진 저장값은 빈 목록으로 처리한다", () => {
    localStorage.setItem("kopilot.conversations", "{깨진 json");

    expect(listConversations()).toEqual([]);
  });

  it("id 없는 항목은 걸러낸다", () => {
    localStorage.setItem("kopilot.conversations", '[{"title":"id 없음"}]');

    expect(listConversations()).toEqual([]);
  });
});
