import { render, screen, fireEvent } from "@testing-library/react";
import { describe, it, expect, vi } from "vitest";
import SidebarNav from "../SidebarNav";

const DAY = 24 * 60 * 60 * 1000;

const conversations = [
  { id: "sess-1", title: "삼성전자 수익률 갭", updatedAt: Date.now() },
  { id: "sess-2", title: "KODEX 200 괴리율", updatedAt: Date.now() - DAY },
];

describe("SidebarNav", () => {
  it("대화가 없으면 안내 문구를 보여준다", () => {
    render(<SidebarNav open onClose={() => {}} conversations={[]} />);

    expect(screen.getByText("아직 대화 내역이 없습니다")).toBeInTheDocument();
  });

  it("대화를 날짜별로 묶어 보여준다", () => {
    render(<SidebarNav open onClose={() => {}} conversations={conversations} />);

    expect(screen.getByText("오늘")).toBeInTheDocument();
    expect(screen.getByText("어제")).toBeInTheDocument();
    expect(screen.getByText("삼성전자 수익률 갭")).toBeInTheDocument();
    expect(screen.getByText("KODEX 200 괴리율")).toBeInTheDocument();
  });

  it("대화를 누르면 onSelectConversation에 id를 넘기고 사이드바를 닫는다", () => {
    const onSelectConversation = vi.fn();
    const onClose = vi.fn();
    render(
      <SidebarNav
        open
        onClose={onClose}
        conversations={conversations}
        onSelectConversation={onSelectConversation}
      />
    );

    fireEvent.click(screen.getByText("KODEX 200 괴리율"));

    expect(onSelectConversation).toHaveBeenCalledWith("sess-2");
    expect(onClose).toHaveBeenCalled();
  });

  it("삭제 버튼은 onDeleteConversation만 부르고 대화를 열지 않는다", () => {
    const onSelectConversation = vi.fn();
    const onDeleteConversation = vi.fn();
    render(
      <SidebarNav
        open
        onClose={() => {}}
        conversations={conversations}
        onSelectConversation={onSelectConversation}
        onDeleteConversation={onDeleteConversation}
      />
    );

    fireEvent.click(
      screen.getByRole("button", { name: "삼성전자 수익률 갭 대화 삭제" })
    );

    expect(onDeleteConversation).toHaveBeenCalledWith("sess-1");
    expect(onSelectConversation).not.toHaveBeenCalled();
  });
});
