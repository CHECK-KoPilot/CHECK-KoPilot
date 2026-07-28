import { describe, it, expect } from "vitest";
import { buildPrompt, needsPeriod, stockLabel, nameOf, PERIODS } from "../promptTemplate";

describe("stockLabel / nameOf", () => {
  it("칩 라벨은 이름(코드) 형식이다", () => {
    expect(stockLabel({ name: "삼성전자", code: "005930" })).toBe("삼성전자(005930)");
  });

  it("이름만 다시 꺼낼 수 있다", () => {
    expect(nameOf("삼성전자(005930)")).toBe("삼성전자");
    expect(nameOf("TIGER 미국S&P500(360750)")).toBe("TIGER 미국S&P500");
    expect(nameOf("코스피")).toBe("코스피"); // 코드가 없는 표기도 그대로 통과
  });
});

describe("needsPeriod", () => {
  it("{period}가 있으면 기간이 필요하다", () => {
    expect(needsPeriod("{targets}의 {period} 변동성을 계산해줘")).toBe(true);
    expect(needsPeriod("{targets}의 괴리율을 알려줘")).toBe(false);
  });
});

describe("buildPrompt", () => {
  it("종목명만 넣고 기간을 치환한다", () => {
    expect(
      buildPrompt({
        template: "{targets}의 {period} 수익률 갭을 비교해줘",
        targetLabels: ["삼성전자(005930)", "SK하이닉스(000660)"],
        periodCode: "3M",
      })
    ).toBe("삼성전자와 SK하이닉스의 최근 3개월 수익률 갭을 비교해줘");
  });

  it("받침이 있으면 '과'로 잇는다", () => {
    expect(
      buildPrompt({
        template: "{targets}의 {period} 수익률 갭을 비교해줘",
        targetLabels: ["SK하이닉스(000660)", "삼성전자(005930)"],
        periodCode: "1M",
      })
    ).toBe("SK하이닉스와 삼성전자의 최근 1개월 수익률 갭을 비교해줘");
  });

  it("셋 이상은 쉼표로 잇는다", () => {
    expect(
      buildPrompt({
        template: "{targets}의 {period} 수익률 순위를 매겨줘",
        targetLabels: ["에코프로(086520)", "엘앤에프(066970)", "포스코퓨처엠(003670)"],
        periodCode: "6M",
      })
    ).toBe("에코프로, 엘앤에프, 포스코퓨처엠의 최근 6개월 수익률 순위를 매겨줘");
  });

  it("{period}가 없는 템플릿은 기간을 무시한다", () => {
    expect(
      buildPrompt({
        template: "{targets}의 괴리율을 알려줘",
        targetLabels: ["KODEX 200(069500)"],
        periodCode: "3M",
      })
    ).toBe("KODEX 200의 괴리율을 알려줘");
  });

  it("기간을 안 고르면 기간 표현 없이 문장이 이어진다", () => {
    expect(
      buildPrompt({
        template: "{targets}의 {period} 변동성을 계산해줘",
        targetLabels: ["삼성전자(005930)"],
        periodCode: null,
      })
    ).toBe("삼성전자의 변동성을 계산해줘");
  });
});

describe("PERIODS", () => {
  it("코드 4종을 갖는다", () => {
    expect(PERIODS.map((p) => p.code)).toEqual(["1M", "3M", "6M", "1Y"]);
  });
});
