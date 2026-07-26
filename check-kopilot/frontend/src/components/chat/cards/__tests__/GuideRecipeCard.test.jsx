import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import GuideRecipeCard, { implementationPrompt } from "../GuideRecipeCard";

const message = {
  topic: "외국인 순매수 동향",
  matched: [
    {
      apiId: "stock-investor",
      name: "투자자별 매매동향",
      path: "/stock/m001/invest_trend",
      summary: "종목별 기관·외국인·개인 순매수 추이",
      params: [{ name: "code", required: true }, { name: "fromDate", required: true }],
      docUrl: "https://checkapi.koscom.co.kr/spec/stock-investor",
      fields: [{ code: "F0001", label: "외국인 순매수" }],
    },
  ],
  catalog: [{ apiId: "stock-investor", name: "투자자별 매매동향", summary: "종목별 순매수 추이" }],
};

describe("GuideRecipeCard", () => {
  beforeEach(() => {
    vi.stubGlobal(
      "fetch",
      vi.fn(() => Promise.resolve({ ok: true }))
    );
  });

  it("필요 API·명세 링크·파라미터를 렌더한다", () => {
    render(<GuideRecipeCard message={message} />);

    expect(screen.getByText(/현재 카탈로그에 없는 지표입니다/)).toBeInTheDocument();
    expect(screen.getAllByText("투자자별 매매동향").length).toBeGreaterThan(0);
    expect(screen.getByText(message.matched[0].summary)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /명세 보기/ })).toHaveAttribute(
      "href",
      message.matched[0].docUrl
    );
    expect(screen.getByText("외국인 순매수")).toBeInTheDocument();
    expect(screen.getByText("code*")).toBeInTheDocument();
    expect(screen.getByText("fromDate*")).toBeInTheDocument();
  });

  it("다른 후보 API 목록은 기본적으로 접혀 있다가 토글하면 펼쳐진다", () => {
    render(<GuideRecipeCard message={message} />);

    expect(screen.getAllByText("투자자별 매매동향")).toHaveLength(1);

    fireEvent.click(screen.getByRole("button", { name: /다른 후보 API \(1건\)/ }));

    expect(screen.getAllByText("투자자별 매매동향")).toHaveLength(2);
  });

  it("'카탈로그 추가 요청' 버튼을 누르면 /api/catalog-requests에 세션·주제·매칭 API를 전송한다", async () => {
    render(<GuideRecipeCard message={message} />);

    fireEvent.click(screen.getByRole("button", { name: /카탈로그 추가 요청/ }));

    await waitFor(() => expect(screen.getByText("요청 완료")).toBeInTheDocument());

    expect(fetch).toHaveBeenCalledWith(
      "/api/catalog-requests",
      expect.objectContaining({
        method: "POST",
        body: expect.stringContaining('"topic":"외국인 순매수 동향"'),
      })
    );
    const [, options] = fetch.mock.calls[0];
    const body = JSON.parse(options.body);
    expect(body.matchedApiIds).toBe("stock-investor");
    expect(typeof body.sessionId).toBe("string");
  });
});

describe("구현 방법 자세히 버튼", () => {
  it("이미 찾은 API를 지목한 후속 질문을 다음 턴으로 보낸다", async () => {
    // 같은 검색을 반복하지 않고 곧바로 상세로 들어가게 한다
    const prompt = implementationPrompt("외국인 순매수", [{ name: "[일별정보] 투자자" }]);

    expect(prompt).toContain("외국인 순매수");
    expect(prompt).toContain("[일별정보] 투자자");
    expect(prompt).toContain("F코드");
  });

  it("매칭된 API가 없으면 종목 언급 없이도 질문을 만든다", () => {
    expect(implementationPrompt("공매도 잔고", [])).toContain("공매도 잔고");
  });
});
