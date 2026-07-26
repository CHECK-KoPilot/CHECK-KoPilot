import { render, screen } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import AssistantText from "../AssistantText";

describe("AssistantText", () => {
  it("**굵게**와 `코드`를 태그로 바꾼다 — 마크다운 기호가 화면에 남지 않는다", () => {
    const { container } = render(
      <AssistantText text="1. **필요 API**: `F15001`을 씁니다" />
    );

    expect(screen.getByText("필요 API").tagName).toBe("STRONG");
    expect(screen.getByText("F15001").tagName).toBe("CODE");
    expect(container.textContent).not.toContain("**");
    expect(container.textContent).toContain("1. 필요 API");
  });

  it("코드 펜스를 pre 블록으로 분리한다", () => {
    render(
      <AssistantText text={'예시:\n```json\n{ "cust_id": "..." }\n```\n끝'} />
    );

    const code = screen.getByText(/"cust_id"/);
    expect(code.closest("pre")).not.toBeNull();
    expect(screen.getByText("예시:")).toBeInTheDocument();
    expect(screen.getByText("끝")).toBeInTheDocument();
  });

  it("마크다운이 없는 평문은 줄바꿈을 유지한 채 그대로 통과시킨다", () => {
    const plain = "최근 한 달 수익률은 -22.76%입니다.\n최고가는 343,000원입니다.";
    const { container } = render(<AssistantText text={plain} />);

    expect(container.textContent).toBe(plain);
  });

  it("코드 펜스만 있는 응답은 pre 블록 하나로 끝난다", () => {
    const { container } = render(<AssistantText text={"```\ncode\n```"} />);

    expect(container.querySelectorAll("pre")).toHaveLength(1);
    expect(container.querySelectorAll("p")).toHaveLength(0);
  });
});
