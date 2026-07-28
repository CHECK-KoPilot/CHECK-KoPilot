import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach } from "vitest";
import StockPicker from "../StockPicker";

beforeEach(() => {
  vi.stubGlobal("fetch", vi.fn(() =>
    Promise.resolve({
      ok: true,
      status: 200,
      json: () => Promise.resolve([
        { code: "005930", name: "삼성전자", market: "KOSPI", type: "STOCK" },
        { code: "006400", name: "삼성SDI", market: "KOSPI", type: "STOCK" },
      ]),
    })
  ));
});

describe("StockPicker", () => {
  it("두 글자 이상 입력하면 후보를 띄우고, 고르면 칩으로 담는다", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(<StockPicker value={[]} onChange={onChange} max={2} />);

    await user.type(screen.getByRole("combobox", { name: "종목 검색" }), "삼성");

    await waitFor(() => expect(screen.getByText("삼성전자")).toBeInTheDocument());
    await user.click(screen.getByText("삼성전자"));

    expect(onChange).toHaveBeenCalledWith(["삼성전자(005930)"]);
  });

  it("담긴 칩을 지울 수 있다", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(<StockPicker value={["삼성전자(005930)"]} onChange={onChange} max={2} />);

    await user.click(screen.getByRole("button", { name: "삼성전자(005930) 제거" }));

    expect(onChange).toHaveBeenCalledWith([]);
  });

  it("최대 개수를 채우면 입력을 막는다", () => {
    render(<StockPicker value={["삼성전자(005930)"]} onChange={vi.fn()} max={1} />);

    expect(screen.getByRole("combobox", { name: "종목 검색" })).toBeDisabled();
  });

  it("검색 결과가 없으면 후보를 띄우지 않는다", async () => {
    vi.unstubAllGlobals();
    vi.stubGlobal("fetch", vi.fn(() =>
      Promise.resolve({
        ok: true,
        status: 200,
        json: () => Promise.resolve([]),
      })
    ));

    const user = userEvent.setup();
    const onChange = vi.fn();
    render(<StockPicker value={[]} onChange={onChange} max={2} />);

    await user.type(screen.getByRole("combobox", { name: "종목 검색" }), "존재없음");

    // 빈 결과는 자동완성 리스트를 띄우지 않는다 — 검색어 입력 후 일정 시간 대기 (debounce)
    await new Promise((r) => setTimeout(r, 300)); // debounce + margin

    // 리스트가 없어야 한다
    expect(screen.queryByRole("list")).not.toBeInTheDocument();
    expect(onChange).not.toHaveBeenCalled();
  });

  it("검색 실패 시 입력은 계속되고 자동완성만 비워진다", async () => {
    vi.unstubAllGlobals();
    vi.stubGlobal("fetch", vi.fn(() =>
      Promise.reject(new Error("Network error"))
    ));

    const user = userEvent.setup();
    const onChange = vi.fn();
    render(<StockPicker value={[]} onChange={onChange} max={2} />);

    await user.type(screen.getByRole("combobox", { name: "종목 검색" }), "테스트");

    // 입력은 그대로 남아있다
    expect(screen.getByRole("combobox", { name: "종목 검색" })).toHaveValue("테스트");

    // 자동완성은 띄워지지 않는다 — 검색 실패 후 일정 시간 대기 (debounce)
    await new Promise((r) => setTimeout(r, 300)); // debounce + margin

    // 리스트가 없어야 한다
    expect(screen.queryByRole("list")).not.toBeInTheDocument();
    expect(onChange).not.toHaveBeenCalled();
  });

  it("중복된 종목은 다시 담지 않는다", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(<StockPicker value={["삼성전자(005930)"]} onChange={onChange} max={2} />);

    await user.type(screen.getByRole("combobox", { name: "종목 검색" }), "삼성");

    await waitFor(() => expect(screen.getByText("삼성전자")).toBeInTheDocument());
    await user.click(screen.getByText("삼성전자"));

    // onChange가 호출되지 않는다 (중복 방지)
    expect(onChange).not.toHaveBeenCalled();
  });
});
