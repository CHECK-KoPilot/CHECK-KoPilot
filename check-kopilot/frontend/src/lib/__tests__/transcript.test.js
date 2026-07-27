import { describe, it, expect, beforeEach } from "vitest";
import { loadTranscript, saveTranscript, clearTranscript } from "../transcript";

describe("transcript", () => {
  beforeEach(() => localStorage.clear());

  it("저장한 대화를 그대로 복원한다", () => {
    // 새로고침해도 서버는 세션 컨텍스트를 기억하므로 화면도 같이 살아나야 한다
    const messages = [
      { id: "u-1", type: "user", text: "삼성전자 시세 요약" },
      { id: "a-1", type: "indicator", cardId: "c1", headline: [{ label: "수익률", value: 5 }] },
    ];
    saveTranscript("sess-1", messages);

    expect(loadTranscript("sess-1")).toEqual(messages);
  });

  it("세션끼리 섞이지 않는다", () => {
    saveTranscript("sess-1", [{ id: "u-1", type: "user", text: "A" }]);

    expect(loadTranscript("sess-2")).toEqual([]);
  });

  it("새 대화는 화면 기록을 지운다", () => {
    saveTranscript("sess-1", [{ id: "u-1", type: "user", text: "A" }]);

    clearTranscript("sess-1");

    expect(loadTranscript("sess-1")).toEqual([]);
  });

  it("깨진 저장값은 빈 대화로 처리한다", () => {
    localStorage.setItem("kopilot.transcript.sess-1", "{깨진 json");

    expect(loadTranscript("sess-1")).toEqual([]);
  });

  it("배열이 아닌 저장값도 빈 대화로 처리한다", () => {
    localStorage.setItem("kopilot.transcript.sess-1", '{"not":"array"}');

    expect(loadTranscript("sess-1")).toEqual([]);
  });
});
