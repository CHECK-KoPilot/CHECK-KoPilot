import { act, render, screen, waitFor } from "@testing-library/react";
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
    const fetchMock = vi.fn(() =>
      Promise.resolve({
        ok: true,
        status: 200,
        json: () => Promise.resolve([]),
      })
    );
    vi.stubGlobal("fetch", fetchMock);

    const user = userEvent.setup();
    const onChange = vi.fn();
    render(<StockPicker value={[]} onChange={onChange} max={2} />);

    await user.type(screen.getByRole("combobox", { name: "종목 검색" }), "존재없음");

    // 검색이 실제로 나갈 때까지 기다린다. 리스트 부재를 곧바로 waitFor하면
    // debounce 발사 전 초기 상태에서 첫 폴에 통과해버려 아무것도 검증하지 못한다.
    await waitFor(() => expect(fetchMock).toHaveBeenCalled());
    await act(async () => {}); // setResults가 반영되도록 마이크로태스크를 비운다

    expect(screen.queryByRole("list")).not.toBeInTheDocument();
    expect(onChange).not.toHaveBeenCalled();
  });

  it("검색 실패 시 입력은 계속되고 자동완성만 비워진다", async () => {
    vi.unstubAllGlobals();
    const fetchMock = vi.fn(() => Promise.reject(new Error("Network error")));
    vi.stubGlobal("fetch", fetchMock);

    const user = userEvent.setup();
    const onChange = vi.fn();
    render(<StockPicker value={[]} onChange={onChange} max={2} />);

    await user.type(screen.getByRole("combobox", { name: "종목 검색" }), "테스트");

    // 검색이 실제로 나가 실패할 때까지 기다린다 — 그래야 .catch 분기를 지난 상태를 본다
    await waitFor(() => expect(fetchMock).toHaveBeenCalled());
    await act(async () => {});

    // 입력은 그대로 남고 자동완성만 비워진다
    expect(screen.getByRole("combobox", { name: "종목 검색" })).toHaveValue("테스트");
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
