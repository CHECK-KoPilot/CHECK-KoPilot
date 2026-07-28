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

  // #100. rawData가 비면 Object.keys(rawRows[0])가 터졌다. ErrorBoundary가 없어
  // 카드 한 장의 렌더 예외가 React 트리 전체를 언마운트해 화면이 백지가 된다.
  it("rawData가 비어도 크래시하지 않고 나머지 근거를 보여준다", () => {
    render(<EvidencePanel evidence={{ ...evidence, rawData: [] }} />);
    fireEvent.click(screen.getByRole("button", { name: /근거 보기/ }));

    expect(screen.getByText("원본 데이터가 없습니다")).toBeInTheDocument();
    expect(screen.getByText(evidence.formula)).toBeInTheDocument();
  });

  // 시리즈는 있는데 rows만 빈 경우도 rawRows가 []가 된다
  it("시리즈의 rows가 비어도 크래시하지 않는다", () => {
    render(
      <EvidencePanel
        evidence={{ ...evidence, rawData: [{ name: "삼성전자", rows: [] }] }}
      />
    );
    fireEvent.click(screen.getByRole("button", { name: /근거 보기/ }));

    expect(screen.getByText("원본 데이터가 없습니다")).toBeInTheDocument();
  });

  // 백엔드 계약상 evidence 4종은 항상 채워지지만, 누락 시 앱 전체가 죽는 것은 과한 대가다
  it("evidence 필드가 누락돼도 크래시하지 않는다", () => {
    render(<EvidencePanel evidence={{}} />);
    fireEvent.click(screen.getByRole("button", { name: /근거 보기/ }));

    expect(screen.getByText("원본 데이터가 없습니다")).toBeInTheDocument();
  });
});
