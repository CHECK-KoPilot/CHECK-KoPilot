import { renderHook, waitFor, act } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { useShortcuts } from "../useShortcuts";

const SHORTCUT = {
  id: "s1",
  keyCombo: "ctrl+shift+1",
  toolName: "return_gap",
  targets: ["삼성전자(005930)", "SK하이닉스(000660)"],
  period: "3M",
  prompt: "삼성전자와 SK하이닉스의 최근 3개월 수익률 갭을 비교해줘",
};

function pressCtrlShift1({ isComposing = false } = {}) {
  const event = new KeyboardEvent("keydown", {
    code: "Digit1",
    ctrlKey: true,
    shiftKey: true,
    bubbles: true,
    cancelable: true,
  });
  // jsdom의 KeyboardEvent는 isComposing을 생성자로 못 받는다
  Object.defineProperty(event, "isComposing", { value: isComposing });
  act(() => { window.dispatchEvent(event); });
  return event;
}

beforeEach(() => {
  localStorage.clear();
  vi.stubGlobal("fetch", vi.fn(() =>
    Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve([SHORTCUT]) })
  ));
});

describe("useShortcuts", () => {
  it("마운트하면 목록을 불러온다", async () => {
    const { result } = renderHook(() => useShortcuts({ onTrigger: vi.fn(), enabled: true }));

    await waitFor(() => expect(result.current.shortcuts).toHaveLength(1));
    expect(result.current.loadError).toBe(false);
  });

  it("등록된 키를 누르면 저장된 프롬프트로 onTrigger를 부른다", async () => {
    const onTrigger = vi.fn();
    const { result } = renderHook(() => useShortcuts({ onTrigger, enabled: true }));
    await waitFor(() => expect(result.current.shortcuts).toHaveLength(1));

    const event = pressCtrlShift1();

    expect(onTrigger).toHaveBeenCalledWith(SHORTCUT.prompt);
    expect(event.defaultPrevented).toBe(true);
  });

  it("한글 입력 조합 중에는 발사하지 않는다", async () => {
    const onTrigger = vi.fn();
    const { result } = renderHook(() => useShortcuts({ onTrigger, enabled: true }));
    await waitFor(() => expect(result.current.shortcuts).toHaveLength(1));

    pressCtrlShift1({ isComposing: true });

    expect(onTrigger).not.toHaveBeenCalled();
  });

  it("enabled=false면 발사하지 않는다", async () => {
    const onTrigger = vi.fn();
    const { result } = renderHook(() => useShortcuts({ onTrigger, enabled: false }));
    await waitFor(() => expect(result.current.shortcuts).toHaveLength(1));

    pressCtrlShift1();

    expect(onTrigger).not.toHaveBeenCalled();
  });

  it("등록되지 않은 키는 브라우저 기본 동작을 막지 않는다", async () => {
    const onTrigger = vi.fn();
    const { result } = renderHook(() => useShortcuts({ onTrigger, enabled: true }));
    await waitFor(() => expect(result.current.shortcuts).toHaveLength(1));

    const event = new KeyboardEvent("keydown", {
      code: "Digit9", ctrlKey: true, shiftKey: true, bubbles: true, cancelable: true,
    });
    act(() => { window.dispatchEvent(event); });

    expect(onTrigger).not.toHaveBeenCalled();
    expect(event.defaultPrevented).toBe(false);
  });

  it("목록 로드가 실패하면 loadError를 세운다", async () => {
    vi.stubGlobal("fetch", vi.fn(() => Promise.reject(new Error("network"))));
    const { result } = renderHook(() => useShortcuts({ onTrigger: vi.fn(), enabled: true }));

    await waitFor(() => expect(result.current.loadError).toBe(true));
    expect(result.current.shortcuts).toEqual([]);
  });
});
