import { describe, it, expect, vi, afterEach } from "vitest";
import { buildTourSteps } from "../tourSteps";

const originalInnerWidth = window.innerWidth;

afterEach(() => {
  window.innerWidth = originalInnerWidth;
});

describe("buildTourSteps", () => {
  it("주요 UI 요소를 안내하는 6단계 셀렉터를 이 순서대로 반환한다", () => {
    const steps = buildTourSteps({ setSidebarOpen: vi.fn() });

    expect(steps.map((s) => s.selector)).toEqual([
      '[data-tour="new-chat"]',
      '[data-tour="suggested-prompts"]',
      '[data-tour="chat-input"]',
      '[data-tour="key-metrics"]',
      '[data-tour="evidence-toggle"]',
      '[data-tour="excel-download"]',
    ]);
  });

  it("모바일 화면에서만 새 대화 단계가 사이드바를 열고 닫는다", () => {
    const setSidebarOpen = vi.fn();
    const [newChatStep] = buildTourSteps({ setSidebarOpen });

    window.innerWidth = 500;
    newChatStep.beforeShow();
    newChatStep.afterHide();
    expect(setSidebarOpen).toHaveBeenNthCalledWith(1, true);
    expect(setSidebarOpen).toHaveBeenNthCalledWith(2, false);

    setSidebarOpen.mockClear();
    window.innerWidth = 1280;
    newChatStep.beforeShow();
    newChatStep.afterHide();
    expect(setSidebarOpen).not.toHaveBeenCalled();
  });
});
