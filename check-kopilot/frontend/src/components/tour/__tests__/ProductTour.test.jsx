import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import ProductTour from "../ProductTour";

const steps = [
  { selector: '[data-tour="step-a"]', title: "첫 번째 안내", description: "첫 번째 설명" },
  { selector: '[data-tour="step-b"]', title: "두 번째 안내", description: "두 번째 설명" },
];

function renderTargets() {
  const a = document.createElement("div");
  a.setAttribute("data-tour", "step-a");
  const b = document.createElement("div");
  b.setAttribute("data-tour", "step-b");
  document.body.append(a, b);
}

beforeEach(() => {
  document.body.innerHTML = "";
  renderTargets();
  Element.prototype.scrollIntoView = vi.fn();
  Element.prototype.getBoundingClientRect = () => ({
    top: 100,
    bottom: 140,
    left: 20,
    right: 220,
    width: 200,
    height: 40,
  });
});

describe("ProductTour", () => {
  it("첫 단계를 보여주고 다음/이전 버튼으로 단계를 이동하다 마지막에 닫힌다", async () => {
    const onClose = vi.fn();
    render(<ProductTour steps={steps} onClose={onClose} />);

    await waitFor(() => expect(screen.getByText("첫 번째 안내")).toBeInTheDocument());
    expect(screen.getByText("1 / 2")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: /다음/ }));
    await waitFor(() => expect(screen.getByText("두 번째 안내")).toBeInTheDocument());

    fireEvent.click(screen.getByRole("button", { name: /완료/ }));
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it("닫기 버튼을 누르면 onClose를 호출한다", async () => {
    const onClose = vi.fn();
    render(<ProductTour steps={steps} onClose={onClose} />);

    await waitFor(() => expect(screen.getByText("첫 번째 안내")).toBeInTheDocument());
    fireEvent.click(screen.getByRole("button", { name: "투어 닫기" }));

    expect(onClose).toHaveBeenCalledTimes(1);
  });
});
