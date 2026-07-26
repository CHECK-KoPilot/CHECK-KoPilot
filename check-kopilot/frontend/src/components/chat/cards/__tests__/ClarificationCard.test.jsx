import { render, screen, fireEvent } from "@testing-library/react";
import { describe, it, expect, vi } from "vitest";
import ClarificationCard from "../ClarificationCard";

const message = {
  query: "삼성",
  candidates: [
    { code: "005930", name: "삼성전자", market: "KOSPI" },
    { code: "009150", name: "삼성전기", market: "KOSPI" },
  ],
};

describe("ClarificationCard", () => {
  it("검색어와 후보 종목 칩을 렌더하고, 클릭하면 onSelect에 후보를 전달한다", () => {
    const onSelect = vi.fn();
    render(<ClarificationCard message={message} onSelect={onSelect} />);

    expect(screen.getByText(/'삼성' 검색 결과가 여러 개입니다/)).toBeInTheDocument();
    expect(screen.getByText("삼성전자")).toBeInTheDocument();
    expect(screen.getByText("삼성전기")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: /삼성전자/ }));
    expect(onSelect).toHaveBeenCalledWith(message.candidates[0]);
  });
});
