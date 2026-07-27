import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import GuideRecipeCard from "../GuideRecipeCard";

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

describe("카탈로그에 있는 지표의 구현 방법(knownMetric)", () => {
  const knownMessage = {
    topic: "삼성전자 vs 코스피 수익률 갭",
    knownMetric: true,
    matched: [{ apiId: "stock-daily", name: "[일별정보]", path: "/stock/m001/hist_info" }],
    catalog: [],
  };

  it("'카탈로그에 없는 지표'라고 말하지 않는다", () => {
    render(<GuideRecipeCard message={knownMessage} />);

    expect(screen.queryByText(/카탈로그에 없는 지표/)).not.toBeInTheDocument();
    expect(screen.getByText(/직접 구현하기/)).toBeInTheDocument();
  });

  it("이미 카탈로그에 있으므로 '카탈로그 추가 요청' 버튼을 숨긴다", () => {
    render(<GuideRecipeCard message={knownMessage} />);

    expect(screen.queryByRole("button", { name: /카탈로그 추가 요청/ })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: /레시피 저장/ })).toBeInTheDocument();
  });

  it("카탈로그 밖 지표는 기존 문구와 버튼을 그대로 쓴다", () => {
    render(<GuideRecipeCard message={{ topic: "공매도 잔고", matched: [], catalog: [] }} />);

    expect(screen.getByText(/카탈로그에 없는 지표/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /카탈로그 추가 요청/ })).toBeInTheDocument();
  });
});
