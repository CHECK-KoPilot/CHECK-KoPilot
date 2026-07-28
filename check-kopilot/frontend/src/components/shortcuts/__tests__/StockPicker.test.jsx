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
});
