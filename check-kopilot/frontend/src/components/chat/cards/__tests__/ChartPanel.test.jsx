import { render, screen } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import ChartPanel from "../ChartPanel";

const lineChart = {
  chartType: "line",
  series: [
    { name: "삼성전자", points: [{ label: "06/25", value: 0 }, { label: "07/25", value: 8.42 }] },
    { name: "코스피", points: [{ label: "06/25", value: 0 }, { label: "07/25", value: 3.15 }] },
  ],
};

const barChart = {
  chartType: "bar",
  series: [{ name: "연환산 변동성", points: [{ label: "SK하이닉스", value: 24.8 }] }],
};

describe("ChartPanel", () => {
  it("line 차트는 '추이' 라벨을 표시한다", () => {
    render(<ChartPanel chart={lineChart} />);
    expect(screen.getByText("추이")).toBeInTheDocument();
  });

  it("bar 차트는 '비교' 라벨을 표시한다", () => {
    render(<ChartPanel chart={barChart} />);
    expect(screen.getByText("비교")).toBeInTheDocument();
  });
});
