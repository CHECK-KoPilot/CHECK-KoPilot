import { describe, it, expect } from "vitest";
import {
  guideImplementationPrompt,
  indicatorImplementationPrompt,
} from "../implementationPrompt";

describe("guideImplementationPrompt", () => {
  it("이미 찾은 API를 지목한 후속 질문을 만든다", () => {
    // 같은 검색을 반복하지 않고 곧바로 상세로 들어가게 한다
    const prompt = guideImplementationPrompt("외국인 순매수", [{ name: "[일별정보] 투자자" }]);

    expect(prompt).toContain("외국인 순매수");
    expect(prompt).toContain("[일별정보] 투자자");
    expect(prompt).toContain("F코드");
  });

  it("매칭된 API가 없으면 API 언급 없이도 질문을 만든다", () => {
    expect(guideImplementationPrompt("공매도 잔고", [])).toContain("공매도 잔고");
  });
});

describe("indicatorImplementationPrompt", () => {
  const card = {
    title: "삼성전자 vs 코스피 수익률 갭 (2026-06-27 ~ 2026-07-27)",
    evidence: {
      apiCalls: [
        { apiId: "stock-daily", api: "일별 시세 조회", request: "삼성전자(005930)" },
        { apiId: "index-daily", api: "일별 시세 조회", request: "코스피(KOSPI)" },
      ],
      formula: "기간수익률(%) = (기간 마지막 종가 / 기간 첫 종가 - 1) * 100",
    },
  };

  it("카드가 실제로 호출한 apiId와 공식을 질문에 싣는다", () => {
    // 이게 없으면 explain_recipe가 키워드로 엉뚱한 API를 집어 근거 패널과 어긋난다
    const prompt = indicatorImplementationPrompt(card);

    expect(prompt).toContain("stock-daily");
    expect(prompt).toContain("index-daily");
    expect(prompt).toContain("기간 마지막 종가");
    expect(prompt).toContain("수익률 갭");
  });

  it("apiId가 없는 예전 카드도 API 이름만으로 질문을 만든다", () => {
    const legacy = {
      title: "카카오 20일선 이격도",
      evidence: { apiCalls: [{ api: "일별 시세 조회" }], formula: "이격도(%) = ..." },
    };

    const prompt = indicatorImplementationPrompt(legacy);

    expect(prompt).toContain("일별 시세 조회");
    expect(prompt).not.toContain("undefined");
  });

  it("근거가 비어 있어도 터지지 않는다", () => {
    expect(indicatorImplementationPrompt({ title: "무언가" })).toContain("무언가");
    expect(indicatorImplementationPrompt(undefined)).toContain("F코드");
  });
});
