import { render, screen, fireEvent } from "@testing-library/react";
import { describe, it, expect, vi } from "vitest";
import ChatMessageList from "../ChatMessageList";

describe("ChatMessageList", () => {
  it("메시지가 없으면 빈 상태와 튜토리얼 버튼을 보여주고 클릭 시 onStartTour를 호출한다", () => {
    const onStartTour = vi.fn();
    render(<ChatMessageList messages={[]} onStartTour={onStartTour} />);

    expect(screen.getByText("무엇을 도와드릴까요?")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: /튜토리얼/ }));
    expect(onStartTour).toHaveBeenCalledTimes(1);
  });
});
