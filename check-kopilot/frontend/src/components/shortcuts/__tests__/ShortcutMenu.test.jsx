import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach } from "vitest";
import ShortcutMenu from "../ShortcutMenu";

const SHORTCUT = {
  id: "s1",
  keyCombo: "ctrl+shift+1",
  toolName: "return_gap",
  targets: ["삼성전자(005930)", "SK하이닉스(000660)"],
  period: "3M",
  prompt: "삼성전자와 SK하이닉스의 최근 3개월 수익률 갭을 비교해줘",
};

beforeEach(() => {
  localStorage.clear();
  vi.stubGlobal("fetch", vi.fn(() =>
    Promise.resolve({ ok: true, status: 204, json: () => Promise.resolve(null) })
  ));
});

describe("ShortcutMenu", () => {
  it("저장된 단축키를 키 표기와 함께 보여준다", async () => {
    const user = userEvent.setup();
    render(
      <ShortcutMenu shortcuts={[SHORTCUT]} loadError={false} onReload={vi.fn()}
                    onFormOpenChange={vi.fn()} />
    );

    await user.click(screen.getByRole("button", { name: "단축키" }));

    expect(screen.getByText(/Ctrl \+ Shift \+ 1|⌘ \+ ⇧ \+ 1/)).toBeInTheDocument();
    expect(screen.getByText(SHORTCUT.prompt)).toBeInTheDocument();
  });

  it("삭제하면 서버에 지우고 목록을 다시 부른다", async () => {
    const user = userEvent.setup();
    const onReload = vi.fn();
    render(
      <ShortcutMenu shortcuts={[SHORTCUT]} loadError={false} onReload={onReload}
                    onFormOpenChange={vi.fn()} />
    );

    await user.click(screen.getByRole("button", { name: "단축키" }));
    await user.click(screen.getByRole("button", { name: /삭제/ }));

    await waitFor(() => expect(onReload).toHaveBeenCalled());
    expect(fetch).toHaveBeenCalledWith("/api/shortcuts/s1", expect.objectContaining({
      method: "DELETE",
    }));
  });

  it("폼을 열면 상위에 알린다 — 키 캡처 중 단축키가 발사되면 안 된다", async () => {
    const user = userEvent.setup();
    const onFormOpenChange = vi.fn();
    render(
      <ShortcutMenu shortcuts={[]} loadError={false} onReload={vi.fn()}
                    onFormOpenChange={onFormOpenChange} />
    );

    await user.click(screen.getByRole("button", { name: "단축키" }));
    await user.click(screen.getByRole("button", { name: "단축키 추가" }));

    expect(onFormOpenChange).toHaveBeenCalledWith(true);
  });

  it("목록을 못 불러오면 다시 시도를 보여준다", async () => {
    const user = userEvent.setup();
    const onReload = vi.fn();
    render(
      <ShortcutMenu shortcuts={[]} loadError onReload={onReload} onFormOpenChange={vi.fn()} />
    );

    await user.click(screen.getByRole("button", { name: "단축키" }));
    await user.click(screen.getByRole("button", { name: "다시 시도" }));

    expect(onReload).toHaveBeenCalled();
  });

  it("Escape를 누르면 드롭다운이 닫힌다", async () => {
    const user = userEvent.setup();
    render(
      <ShortcutMenu shortcuts={[SHORTCUT]} loadError={false} onReload={vi.fn()}
                    onFormOpenChange={vi.fn()} />
    );

    await user.click(screen.getByRole("button", { name: "단축키" }));
    expect(screen.getByText(SHORTCUT.prompt)).toBeInTheDocument();

    await user.keyboard("{Escape}");

    expect(screen.queryByText(SHORTCUT.prompt)).not.toBeInTheDocument();
  });

  it("바깥을 클릭하면 드롭다운이 닫힌다", async () => {
    const user = userEvent.setup();
    render(
      <div>
        <button type="button">채팅 입력</button>
        <ShortcutMenu shortcuts={[SHORTCUT]} loadError={false} onReload={vi.fn()}
                      onFormOpenChange={vi.fn()} />
      </div>
    );

    await user.click(screen.getByRole("button", { name: "단축키" }));
    expect(screen.getByText(SHORTCUT.prompt)).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "채팅 입력" }));

    expect(screen.queryByText(SHORTCUT.prompt)).not.toBeInTheDocument();
  });

  it("열림 상태를 aria-expanded로 알린다", async () => {
    const user = userEvent.setup();
    render(
      <ShortcutMenu shortcuts={[SHORTCUT]} loadError={false} onReload={vi.fn()}
                    onFormOpenChange={vi.fn()} />
    );

    const toggle = screen.getByRole("button", { name: "단축키" });
    expect(toggle).toHaveAttribute("aria-expanded", "false");

    await user.click(toggle);

    expect(toggle).toHaveAttribute("aria-expanded", "true");
  });
});
