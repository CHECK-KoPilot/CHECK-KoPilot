import { render, screen } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import KeyMetricsPanel from "../KeyMetricsPanel";

describe("KeyMetricsPanel", () => {
  it("퍼센트 단위는 부호를 붙여 표시한다", () => {
    render(
      <KeyMetricsPanel
        headline={[{ label: "수익률 갭", value: 5.27, unit: "%p" }]}
      />
    );
    expect(screen.getByText("수익률 갭")).toBeInTheDocument();
    expect(screen.getByText("+5.27%")).toBeInTheDocument();
  });

  it("퍼센트가 아닌 단위는 값과 단위를 그대로 표시한다", () => {
    render(
      <KeyMetricsPanel headline={[{ label: "NAV(iNAV)", value: 130791.06, unit: "원" }]}
    />);
    expect(screen.getByText("130791.06")).toBeInTheDocument();
    expect(screen.getByText("원")).toBeInTheDocument();
  });
});
