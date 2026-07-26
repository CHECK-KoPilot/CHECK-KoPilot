import { render, screen } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import IndicatorAnswerCard from "../IndicatorAnswerCard";

const message = {
  cardId: "mock-return-gap-0001",
  title: "삼성전자 vs 코스피 수익률 갭 (최근 1개월)",
  from: "2026-06-25",
  to: "2026-07-25",
  targets: [
    { code: "005930", name: "삼성전자" },
    { code: "KOSPI", name: "코스피" },
  ],
  headline: [
    { label: "삼성전자 기간수익률", value: 8.42, unit: "%" },
    { label: "수익률 갭", value: 5.27, unit: "%p" },
  ],
  chart: {
    chartType: "line",
    series: [{ name: "삼성전자", points: [{ label: "06/25", value: 0 }, { label: "07/25", value: 8.42 }] }],
  },
  commentary: "최근 1개월간 삼성전자는 코스피 대비 초과 수익률을 기록했습니다.",
  evidence: {
    apiCalls: [{ api: "주식 일별 시세", request: "005930", specUrl: "https://checkapi.koscom.co.kr/spec/stock-daily" }],
    rawData: [{ name: "삼성전자", rows: [{ date: "2026-06-25", value: 71500 }] }],
    formula: "수익률 갭 = 기간수익률(삼성전자) − 기간수익률(코스피)",
    steps: [{ label: "수익률 갭", detail: "8.42% − 3.15% = 5.27%p" }],
  },
};

describe("IndicatorAnswerCard", () => {
  it("제목·대상 칩·핵심 수치·AI 해설·xlsx 다운로드 링크를 렌더한다", () => {
    render(<IndicatorAnswerCard message={message} />);

    expect(screen.getByText(/삼성전자 vs 코스피 수익률 갭/)).toBeInTheDocument();
    expect(screen.getByText("삼성전자 005930")).toBeInTheDocument();
    expect(screen.getByText("코스피 KOSPI")).toBeInTheDocument();
    expect(screen.getByText("+5.27%")).toBeInTheDocument();
    expect(screen.getByText(message.commentary)).toBeInTheDocument();

    const link = screen.getByRole("link", { name: /xlsx 다운로드/ });
    expect(link).toHaveAttribute("href", `/api/cards/${message.cardId}/xlsx`);
    expect(link).toHaveAttribute("download");
  });
});
