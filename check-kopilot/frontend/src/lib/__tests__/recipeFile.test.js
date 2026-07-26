import { describe, it, expect } from "vitest";
import { buildRecipeMarkdown, safeFileName } from "../recipeFile";

const guide = {
  topic: "외국인 순매수",
  matched: [
    {
      apiId: "stock-investor",
      name: "[일별정보] 투자자",
      path: "/stock/m001/invest_hist_info",
      summary: "반환 필드 122개",
      docUrl: "https://checkapi.koscom.co.kr/stock/m001investhist",
      params: [
        { name: "cust_id", required: true },
        { name: "data_list", required: false },
      ],
      fields: [{ code: "F06508_11", label: "종목투자자별순매수거래량11(외국인)" }],
    },
  ],
  catalog: [{ apiId: "stock-m002-invest_basic_info", name: "[투자자 기본정보]" }],
};

describe("buildRecipeMarkdown", () => {
  it("API 경로·명세 링크·F코드를 담는다", () => {
    const md = buildRecipeMarkdown(guide, "F06508_11을 합산하면 됩니다.");

    expect(md).toContain("# 외국인 순매수 — CHECK API 레시피");
    expect(md).toContain("/stock/m001/invest_hist_info");
    expect(md).toContain("https://checkapi.koscom.co.kr/stock/m001investhist");
    expect(md).toContain("F06508_11");
    expect(md).toContain("종목투자자별순매수거래량11(외국인)");
  });

  it("필수·선택 파라미터를 구분한다", () => {
    const md = buildRecipeMarkdown(guide, "");

    expect(md).toContain("필수 파라미터: cust_id");
    expect(md).toContain("선택 파라미터: data_list");
  });

  it("LLM 해설을 조합 방법으로 싣는다", () => {
    const md = buildRecipeMarkdown(guide, "F06508_11을 합산하면 됩니다.");

    expect(md).toContain("## 조합 방법");
    expect(md).toContain("F06508_11을 합산하면 됩니다.");
  });

  it("해설이 없어도 깨지지 않는다", () => {
    const md = buildRecipeMarkdown(guide, undefined);

    expect(md).not.toContain("## 조합 방법");
    expect(md).toContain("## 필요한 CHECK API");
  });

  it("고지 문구를 항상 넣는다", () => {
    // 화면 밖으로 나가는 산출물이라 고지가 따라가야 한다 (스펙 9절, 문구 변경 금지)
    expect(buildRecipeMarkdown(guide, "")).toContain(
      "본 자료는 AI가 시장 데이터 기반으로 생성한 정보성 자료이며 투자권유가 아닙니다.",
    );
  });

  it("빈 카드에도 예외를 던지지 않는다", () => {
    expect(() => buildRecipeMarkdown({}, undefined)).not.toThrow();
    expect(() => buildRecipeMarkdown(undefined, undefined)).not.toThrow();
  });
});

describe("safeFileName", () => {
  it("파일명에 못 쓰는 문자를 걷어낸다", () => {
    expect(safeFileName('외국인/순매수:"수급"')).toBe("외국인순매수수급");
  });

  it("공백은 하이픈으로 바꾼다", () => {
    expect(safeFileName("외국인 순매수 수급")).toBe("외국인-순매수-수급");
  });

  it("빈 topic은 기본값으로 대체한다", () => {
    expect(safeFileName("")).toBe("recipe");
    expect(safeFileName(undefined)).toBe("recipe");
  });
});
