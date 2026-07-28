import { render, screen, waitFor, fireEvent } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach } from "vitest";
import ShortcutFormModal from "../ShortcutFormModal";

const CATALOG = [
  {
    toolName: "return_gap",
    label: "수익률 갭 비교",
    description: "두 대상의 기간수익률 차이",
    promptTemplate: "{targets}의 {period} 수익률 갭을 비교해줘",
    minTargets: 2,
    maxTargets: 2,
  },
  {
    toolName: "nav_disparity",
    label: "ETF 괴리율",
    description: "ETF 괴리율",
    promptTemplate: "{targets}의 괴리율을 알려줘",
    minTargets: 1,
    maxTargets: 1,
  },
];

function mockFetch({ saveStatus = 201, saveBody = {} } = {}) {
  return vi.fn((url, options = {}) => {
    const href = String(url);
    if (href.startsWith("/api/catalog")) {
      return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve(CATALOG) });
    }
    if (href.startsWith("/api/stocks")) {
      return Promise.resolve({
        ok: true,
        status: 200,
        json: () => Promise.resolve([
          { code: "005930", name: "삼성전자", market: "KOSPI", type: "STOCK" },
          { code: "000660", name: "SK하이닉스", market: "KOSPI", type: "STOCK" },
        ]),
      });
    }
    if (href.startsWith("/api/shortcuts") && options.method === "POST") {
      return Promise.resolve({
        ok: saveStatus < 400,
        status: saveStatus,
        json: () => Promise.resolve(saveBody),
      });
    }
    return Promise.reject(new Error(`unexpected fetch: ${href}`));
  });
}

async function pickStock(user, name) {
  await user.type(screen.getByRole("combobox", { name: "종목 검색" }), "삼성");
  await waitFor(() => expect(screen.getByText(name)).toBeInTheDocument());
  await user.click(screen.getByText(name));
}

beforeEach(() => {
  localStorage.clear();
  vi.stubGlobal("fetch", mockFetch());
});

describe("ShortcutFormModal", () => {
  it("종목·기간을 고르면 프롬프트가 자동으로 만들어진다", async () => {
    const user = userEvent.setup();
    render(<ShortcutFormModal editing={null} existing={[]} onSaved={vi.fn()} onClose={vi.fn()} />);
    await waitFor(() => expect(screen.getByLabelText("분석할 카탈로그")).toBeInTheDocument());

    await pickStock(user, "삼성전자");
    await pickStock(user, "SK하이닉스");

    expect(screen.getByLabelText("프롬프트 예시")).toHaveValue(
      "삼성전자와 SK하이닉스의 최근 3개월 수익률 갭을 비교해줘"
    );
  });

  it("{period}가 없는 지표는 기간 셀렉트를 감춘다", async () => {
    const user = userEvent.setup();
    render(<ShortcutFormModal editing={null} existing={[]} onSaved={vi.fn()} onClose={vi.fn()} />);
    await waitFor(() => expect(screen.getByLabelText("분석할 카탈로그")).toBeInTheDocument());

    await user.selectOptions(screen.getByLabelText("분석할 카탈로그"), "nav_disparity");

    expect(screen.queryByLabelText("기간")).not.toBeInTheDocument();
  });

  it("사용자가 프롬프트를 고치면 이후 선택이 덮어쓰지 않는다", async () => {
    const user = userEvent.setup();
    render(<ShortcutFormModal editing={null} existing={[]} onSaved={vi.fn()} onClose={vi.fn()} />);
    await waitFor(() => expect(screen.getByLabelText("분석할 카탈로그")).toBeInTheDocument());
    await pickStock(user, "삼성전자");

    const prompt = screen.getByLabelText("프롬프트 예시");
    await user.clear(prompt);
    await user.type(prompt, "내가 직접 쓴 질문");
    await pickStock(user, "SK하이닉스");

    expect(prompt).toHaveValue("내가 직접 쓴 질문");
  });

  it("저장하면 서버에 보내고 onSaved를 부른다", async () => {
    const saved = {
      id: "s1", keyCombo: "ctrl+shift+1", toolName: "return_gap",
      targets: ["삼성전자(005930)", "SK하이닉스(000660)"], period: "3M",
      prompt: "삼성전자와 SK하이닉스의 최근 3개월 수익률 갭을 비교해줘",
    };
    vi.stubGlobal("fetch", mockFetch({ saveStatus: 201, saveBody: saved }));
    const user = userEvent.setup();
    const onSaved = vi.fn();
    render(<ShortcutFormModal editing={null} existing={[]} onSaved={onSaved} onClose={vi.fn()} />);
    await waitFor(() => expect(screen.getByLabelText("분석할 카탈로그")).toBeInTheDocument());

    await pickStock(user, "삼성전자");
    await pickStock(user, "SK하이닉스");
    fireEvent.keyDown(screen.getByRole("button", { name: /키 조합/ }), {
      code: "Digit1", ctrlKey: true, shiftKey: true,
    });
    await user.click(screen.getByRole("button", { name: "저장" }));

    await waitFor(() => expect(onSaved).toHaveBeenCalledWith(saved));
  });

  it("종목 수가 모자라면 저장 버튼이 잠긴다", async () => {
    const user = userEvent.setup();
    render(<ShortcutFormModal editing={null} existing={[]} onSaved={vi.fn()} onClose={vi.fn()} />);
    await waitFor(() => expect(screen.getByLabelText("분석할 카탈로그")).toBeInTheDocument());

    await pickStock(user, "삼성전자"); // return_gap은 2개 필요
    fireEvent.keyDown(screen.getByRole("button", { name: /키 조합/ }), {
      code: "Digit1", ctrlKey: true, shiftKey: true,
    });

    expect(screen.getByRole("button", { name: "저장" })).toBeDisabled();
  });

  it("서버가 409를 주면 충돌 메시지를 띄운다", async () => {
    vi.stubGlobal("fetch", mockFetch({
      saveStatus: 409,
      saveBody: { code: "KEY_TAKEN", message: "이미 사용 중인 키 조합입니다: ctrl+shift+1" },
    }));
    const user = userEvent.setup();
    render(<ShortcutFormModal editing={null} existing={[]} onSaved={vi.fn()} onClose={vi.fn()} />);
    await waitFor(() => expect(screen.getByLabelText("분석할 카탈로그")).toBeInTheDocument());

    await pickStock(user, "삼성전자");
    await pickStock(user, "SK하이닉스");
    fireEvent.keyDown(screen.getByRole("button", { name: /키 조합/ }), {
      code: "Digit1", ctrlKey: true, shiftKey: true,
    });
    await user.click(screen.getByRole("button", { name: "저장" }));

    await waitFor(() =>
      expect(screen.getByText(/이미 사용 중인 키 조합/)).toBeInTheDocument()
    );
  });

  it("Escape 키를 누르면 모달이 닫힌다", async () => {
    const onClose = vi.fn();
    render(<ShortcutFormModal editing={null} existing={[]} onSaved={vi.fn()} onClose={onClose} />);
    await waitFor(() => expect(screen.getByLabelText("분석할 카탈로그")).toBeInTheDocument());

    fireEvent.keyDown(window, { key: "Escape" });

    await waitFor(() => expect(onClose).toHaveBeenCalled());
  });

  it("모달이 dialog 역할을 가진다", async () => {
    render(<ShortcutFormModal editing={null} existing={[]} onSaved={vi.fn()} onClose={vi.fn()} />);
    await waitFor(() => expect(screen.getByLabelText("분석할 카탈로그")).toBeInTheDocument());

    expect(screen.getByRole("dialog")).toBeInTheDocument();
  });

  it("편집 모드에서 기존 값들을 미리 채운다", async () => {
    const editing = {
      id: "s1",
      toolName: "return_gap",
      targets: ["삼성전자(005930)", "SK하이닉스(000660)"],
      period: "6M",
      keyCombo: "ctrl+shift+1",
      prompt: "기존 질문",
    };
    render(<ShortcutFormModal editing={editing} existing={[]} onSaved={vi.fn()} onClose={vi.fn()} />);
    await waitFor(() => expect(screen.getByLabelText("분석할 카탈로그")).toBeInTheDocument());

    expect(screen.getByLabelText("분석할 카탈로그")).toHaveValue("return_gap");
    expect(screen.getByRole("button", { name: /키 조합/ })).toHaveTextContent("Ctrl + Shift + 1");
    expect(screen.getByDisplayValue("기존 질문")).toBeInTheDocument();
  });

  it("편집 모드에서는 기존 프롬프트를 자동 갱신하지 않는다", async () => {
    const user = userEvent.setup();
    const editing = {
      id: "s1",
      toolName: "return_gap",
      targets: ["삼성전자(005930)", "SK하이닉스(000660)"],
      period: "3M",
      keyCombo: "ctrl+shift+1",
      prompt: "사용자가 작성한 기존 질문",
    };
    render(<ShortcutFormModal editing={editing} existing={[]} onSaved={vi.fn()} onClose={vi.fn()} />);
    await waitFor(() => expect(screen.getByLabelText("분석할 카탈로그")).toBeInTheDocument());

    const promptField = screen.getByDisplayValue("사용자가 작성한 기존 질문");

    // 기간을 변경해도 프롬프트는 변하지 않아야 함
    await user.selectOptions(screen.getByLabelText("기간"), "6M");

    // 프롬프트가 여전히 사용자가 작성한 내용이어야 함
    expect(promptField).toHaveValue("사용자가 작성한 기존 질문");
  });

  it("편집 모드에서 저장하면 updateShortcut을 호출한다", async () => {
    const saved = {
      id: "s1",
      keyCombo: "ctrl+shift+1",
      toolName: "return_gap",
      targets: ["삼성전자(005930)", "SK하이닉스(000660)"],
      period: "3M",
      prompt: "수정된 질문",
    };
    const fetchMock = vi.fn((url, options = {}) => {
      const href = String(url);
      if (href.startsWith("/api/catalog")) {
        return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve(CATALOG) });
      }
      if (href.startsWith("/api/shortcuts/s1") && options.method === "PUT") {
        return Promise.resolve({
          ok: true,
          status: 200,
          json: () => Promise.resolve(saved),
        });
      }
      if (href.startsWith("/api/stocks")) {
        return Promise.resolve({
          ok: true,
          status: 200,
          json: () => Promise.resolve([
            { code: "005930", name: "삼성전자", market: "KOSPI", type: "STOCK" },
            { code: "000660", name: "SK하이닉스", market: "KOSPI", type: "STOCK" },
          ]),
        });
      }
      return Promise.reject(new Error(`unexpected fetch: ${href}`));
    });
    vi.stubGlobal("fetch", fetchMock);

    const editing = {
      id: "s1",
      toolName: "return_gap",
      targets: ["삼성전자(005930)", "SK하이닉스(000660)"],
      period: "3M",
      keyCombo: "ctrl+shift+1",
      prompt: "기존 질문",
    };
    const onSaved = vi.fn();
    const user = userEvent.setup();
    render(<ShortcutFormModal editing={editing} existing={[]} onSaved={onSaved} onClose={vi.fn()} />);
    await waitFor(() => expect(screen.getByLabelText("분석할 카탈로그")).toBeInTheDocument());

    const promptField = screen.getByDisplayValue("기존 질문");
    await user.clear(promptField);
    await user.type(promptField, "수정된 질문");

    await user.click(screen.getByRole("button", { name: "저장" }));

    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalledWith(
        "/api/shortcuts/s1",
        expect.objectContaining({ method: "PUT" })
      );
      expect(onSaved).toHaveBeenCalledWith(saved);
    });
  });
});
