import { describe, it, expect } from "vitest";
import { comboFromEvent, formatCombo } from "../keyCombo";

/** keydown 이벤트의 최소 형태. code를 쓰는 이유는 Shift와 함께 누르면 key가 "!"로 바뀌기 때문이다. */
function evt({ code, ctrlKey = false, metaKey = false, shiftKey = false, altKey = false }) {
  return { code, ctrlKey, metaKey, shiftKey, altKey };
}

describe("comboFromEvent", () => {
  it("Ctrl+Shift+숫자를 정규화한다", () => {
    expect(comboFromEvent(evt({ code: "Digit1", ctrlKey: true, shiftKey: true })))
      .toBe("ctrl+shift+1");
  });

  it("mac의 Cmd도 ctrl로 저장한다", () => {
    expect(comboFromEvent(evt({ code: "KeyK", metaKey: true, shiftKey: true })))
      .toBe("ctrl+shift+k");
  });

  it("Shift가 없으면 거부한다", () => {
    expect(comboFromEvent(evt({ code: "Digit1", ctrlKey: true }))).toBeNull();
  });

  it("Alt가 섞이면 거부한다", () => {
    expect(comboFromEvent(evt({ code: "Digit1", ctrlKey: true, shiftKey: true, altKey: true })))
      .toBeNull();
  });

  it("숫자·영문이 아닌 키는 거부한다", () => {
    expect(comboFromEvent(evt({ code: "Slash", ctrlKey: true, shiftKey: true }))).toBeNull();
    expect(comboFromEvent(evt({ code: "ShiftLeft", ctrlKey: true, shiftKey: true }))).toBeNull();
  });

  /**
   * Ctrl+Shift+W는 창을 닫고 T는 닫은 탭을 되살린다 — 브라우저가 preventDefault를 무시한다.
   * 등록을 허용하면 폼 작성 중 창이 닫히거나, 저장해둬도 눌릴 때 지표가 아니라 탭이 뜬다.
   */
  it("브라우저 예약 글자는 잡지 않는다", () => {
    for (const code of ["KeyT", "KeyW", "KeyN", "KeyQ", "KeyI", "KeyJ", "KeyP"]) {
      expect(comboFromEvent({ code, ctrlKey: true, shiftKey: true })).toBeNull();
    }
  });

  it("예약되지 않은 글자와 숫자는 그대로 잡는다", () => {
    expect(comboFromEvent({ code: "KeyG", ctrlKey: true, shiftKey: true })).toBe("ctrl+shift+g");
    expect(comboFromEvent({ code: "Digit1", ctrlKey: true, shiftKey: true })).toBe("ctrl+shift+1");
  });
});

describe("formatCombo", () => {
  it("윈도우 표기", () => {
    expect(formatCombo("ctrl+shift+1", false)).toBe("Ctrl + Shift + 1");
  });

  it("mac 표기", () => {
    expect(formatCombo("ctrl+shift+k", true)).toBe("⌘ + ⇧ + K");
  });
});
