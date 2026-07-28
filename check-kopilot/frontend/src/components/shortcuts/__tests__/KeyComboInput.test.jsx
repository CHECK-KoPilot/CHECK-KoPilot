import { render, screen, fireEvent } from "@testing-library/react";
import { describe, it, expect, vi } from "vitest";
import KeyComboInput from "../KeyComboInput";

describe("KeyComboInput", () => {
  it("허용 조합을 누르면 onChange로 정규화 문자열을 준다", () => {
    const onChange = vi.fn();
    render(<KeyComboInput value={null} onChange={onChange} conflictLabel={null} />);

    const button = screen.getByRole("button", { name: /키 조합/ });
    const event = fireEvent.keyDown(button, {
      code: "Digit1", ctrlKey: true, shiftKey: true,
    });

    expect(onChange).toHaveBeenCalledWith("ctrl+shift+1");
  });

  it("Tab 키는 기본 동작을 막지 않아 키보드 네비게이션이 작동한다", () => {
    const onChange = vi.fn();
    render(<KeyComboInput value={null} onChange={onChange} conflictLabel={null} />);

    // Tab 키는 Ctrl/⌘이 없으므로 capture되지 않는다
    fireEvent.keyDown(screen.getByRole("button", { name: /키 조합/ }), {
      code: "Tab", key: "Tab",
    });

    expect(onChange).not.toHaveBeenCalled();
  });

  it("허용되지 않는 조합은 사유를 보여주고 값을 바꾸지 않는다", () => {
    const onChange = vi.fn();
    render(<KeyComboInput value={null} onChange={onChange} conflictLabel={null} />);

    fireEvent.keyDown(screen.getByRole("button", { name: /키 조합/ }), {
      code: "KeyT", ctrlKey: true,
    });

    expect(onChange).not.toHaveBeenCalled();
    expect(screen.getByText(/Shift/)).toBeInTheDocument();
  });

  it("이미 쓰는 조합이면 충돌 상대를 알려준다", () => {
    render(
      <KeyComboInput value="ctrl+shift+1" onChange={vi.fn()} conflictLabel="삼성전자 변동성" />
    );

    expect(screen.getByText(/삼성전자 변동성/)).toBeInTheDocument();
  });
});
