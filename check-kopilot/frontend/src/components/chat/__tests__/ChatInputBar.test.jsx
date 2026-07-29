import { render, screen, fireEvent } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import ChatInputBar from "../ChatInputBar";
import { suggestedPrompts } from "../../../data/mockConversation";

// jsdom엔 matchMedia가 없다 — ChatInputBar의 모바일 placeholder 판별용 훅이 이를 사용한다
beforeEach(() => {
  window.matchMedia = vi.fn().mockReturnValue({
    matches: false,
    addEventListener: () => {},
    removeEventListener: () => {},
  });
});

describe("ChatInputBar", () => {
  it("suggestionsOpen이 true면 예상 질문 칩을 보여준다", () => {
    render(<ChatInputBar suggestionsOpen onToggleSuggestions={() => {}} />);

    expect(screen.getByText(suggestedPrompts[0])).toBeInTheDocument();
  });

  it("suggestionsOpen이 false면 예상 질문 칩을 숨긴다", () => {
    render(
      <ChatInputBar suggestionsOpen={false} onToggleSuggestions={() => {}} />
    );

    expect(screen.queryByText(suggestedPrompts[0])).not.toBeInTheDocument();
  });

  it("토글 버튼을 누르면 onToggleSuggestions를 호출한다", () => {
    const onToggleSuggestions = vi.fn();
    render(
      <ChatInputBar suggestionsOpen onToggleSuggestions={onToggleSuggestions} />
    );

    fireEvent.click(screen.getByRole("button", { name: /예상 질문/ }));

    expect(onToggleSuggestions).toHaveBeenCalledTimes(1);
  });
});
