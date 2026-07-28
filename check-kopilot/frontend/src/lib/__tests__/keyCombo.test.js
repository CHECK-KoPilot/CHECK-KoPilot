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
});

describe("formatCombo", () => {
  it("윈도우 표기", () => {
    expect(formatCombo("ctrl+shift+1", false)).toBe("Ctrl + Shift + 1");
  });

  it("mac 표기", () => {
    expect(formatCombo("ctrl+shift+k", true)).toBe("⌘ + ⇧ + K");
  });
});
