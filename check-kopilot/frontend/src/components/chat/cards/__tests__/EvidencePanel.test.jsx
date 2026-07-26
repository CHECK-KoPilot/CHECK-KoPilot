import { render, screen, fireEvent } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import EvidencePanel from "../EvidencePanel";

const evidence = {
  apiCalls: [
    { api: "주식 일별 시세", request: "005930 / 2026-06-25~2026-07-25", specUrl: "https://checkapi.koscom.co.kr/spec/stock-daily" },
  ],
  rawData: [
    { name: "삼성전자", rows: [{ date: "2026-06-25", value: 71500 }] },
  ],
  formula: "수익률 갭 = 기간수익률(삼성전자) − 기간수익률(코스피)",
  steps: [{ label: "수익률 갭", detail: "8.42% − 3.15% = 5.27%p" }],
};

describe("EvidencePanel", () => {
  it("기본 상태에서는 근거 내용이 숨겨져 있다가 토글하면 공식·API·계산 과정을 보여준다", () => {
    render(<EvidencePanel evidence={evidence} />);
    expect(screen.queryByText(evidence.formula)).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: /근거 보기/ }));

    expect(screen.getByText(evidence.formula)).toBeInTheDocument();
    expect(screen.getByText(evidence.apiCalls[0].api)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /명세 보기/ })).toHaveAttribute(
      "href",
      evidence.apiCalls[0].specUrl
    );
    expect(screen.getByText(evidence.steps[0].label)).toBeInTheDocument();
    expect(screen.getByText(new RegExp(evidence.steps[0].detail))).toBeInTheDocument();
  });
});
